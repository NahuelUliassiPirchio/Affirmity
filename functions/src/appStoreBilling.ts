/**
 * App Store (StoreKit 2) entitlement resolution for the iOS client -- decoded-transaction-payload
 * -> entitlement-doc mapping, and the idempotent write orchestration for the `syncEntitlementIOS`
 * server entry point. Mirrors `billing.ts`'s split for Play: pure logic only here; SDK wiring (the
 * real `SignedDataVerifier` from `@apple/app-store-server-library`, Apple root cert loading, the
 * Production/Sandbox verifier fallback dance) lives in `index.ts`.
 *
 * Trust model, deliberately simpler than Play's: `syncEntitlement` (Android) resolves its write
 * target (`uid`) from the purchase token's own `externalAccountIdentifiers` at the Play API,
 * because an RTDN-driven re-fetch has no caller identity to begin with. StoreKit 2's signed
 * transaction has no equivalent server-side account-identifier field to resolve a uid from
 * independently -- but `syncEntitlementIOS` doesn't need one: it's a direct authenticated client
 * call, so the caller proves both "I made this purchase" (a validly Apple-signed JWS, checked here)
 * and "I am this uid" (a valid Firebase ID token, checked in `index.ts`) in the same request. The
 * decoded token's `uid` claim is used directly as the write target -- no indirection. Follow-up
 * idea (not required now): StoreKit 2's `Product.PurchaseOption.appAccountToken` could bind the two
 * even more tightly if this ever needs hardening against a stolen ID token being paired with
 * someone else's replayed JWS.
 */

import { VerificationException, VerificationStatus } from '@apple/app-store-server-library';
import { createHash } from 'node:crypto';

import type { EntitlementDoc, EntitlementTier } from './billing';

// ---------------------------------------------------------------------------------------------
// Decoded App Store transaction -> entitlement-doc mapping.
// ---------------------------------------------------------------------------------------------

/** Trimmed shape of `@apple/app-store-server-library`'s `JWSTransactionDecodedPayload`, kept as
 * this file's own interface (same convention `billing.ts` uses for `PlaySubscriptionV2`) so this
 * pure logic doesn't need to import the real SDK type. */
export interface AppStoreTransactionPayload {
  transactionId?: string;
  productId?: string;
  expiresDate?: number;
  revocationDate?: number;
  /** StoreKit 2's transaction `type` (e.g. `"Auto-Renewable Subscription"`, `"Non-Consumable"`,
   * `"Consumable"`, `"Non-Renewing Subscription"` -- mirrors
   * `@apple/app-store-server-library`'s `Type` enum values without importing the real SDK type,
   * same convention as the rest of this interface). Always present on a real, verified StoreKit 2
   * transaction JWS. */
  type?: string;
  /** StoreKit environment of the transaction. `createEnvironmentFallbackVerifier` overwrites this
   * with the environment of the verifier that actually succeeded, so it is never taken on trust
   * from the JWS alone. */
  environment?: string;
}

/** The only `AppStoreTransactionPayload.type` value this app grants Pro for -- see Fix 2 doc
 * comment on `toAppStoreEntitlement` below. Mirrors
 * `@apple/app-store-server-library`'s `Type.AUTO_RENEWABLE_SUBSCRIPTION`. */
const AUTO_RENEWABLE_SUBSCRIPTION_TYPE = 'Auto-Renewable Subscription';

/** Hard server-side cap on how long a Sandbox-environment entitlement can live from the moment it
 * is synced. Apple App Review always buys with sandbox-signed transactions against the production
 * build, so Sandbox JWS must be accepted. Sandbox Apple Accounts can only be created by the
 * developer in App Store Connect and the JWS is bound to our bundle ID, so valid sandbox JWS for
 * this app only come from TestFlight builds, developer-signed builds and App Review. The residual
 * exposure is TestFlight testers getting short-lived Pro for free, repeatably (sandbox
 * subscriptions renew every few minutes). The cap is defense in depth: a sandbox grant can never
 * become a long-lived Pro, even with an odd or missing `expiresDate`. */
export const SANDBOX_ENTITLEMENT_MAX_AGE_MILLIS = 24 * 60 * 60 * 1000;

export type AppStoreEntitlementDoc = EntitlementDoc & {
  /** Additive field: which App Store environment verified this transaction. */
  environment: 'Production' | 'Sandbox';
  /** Plaintext App Store transaction id, kept alongside `purchaseTokenHash` for support/debugging
   * -- unlike Play's purchase token, this is not a bearer credential, so there is no secrecy
   * reason to hash-only it. */
  transactionId: string | null;
};

function sha256Hex(input: string): string {
  return createHash('sha256').update(input).digest('hex');
}

/** Maps a verified App Store transaction payload to this app's entitlement doc (mirrors
 * `toEntitlement()` in `billing.ts`, for App Store instead of Play). `revocationDate` present means
 * Apple refunded or revoked the transaction -- that's the only signal this function acts on;
 * everything else (e.g. an unrevoked but time-expired transaction) passes through as `pro` and is
 * the reader's job to resolve via the client's existing `resolveTier(doc, nowMillis)`, exactly like
 * Play's CANCELED state does. */
export function toAppStoreEntitlement(
  payload: AppStoreTransactionPayload,
  nowMillis: number,
): AppStoreEntitlementDoc {
  // A verified StoreKit 2 transaction JWS always carries a transactionId -- its absence means the
  // decoded payload is malformed, not that this specific field is legitimately optional. Reject
  // here rather than falling through to `sha256Hex('')`, which would collapse every such payload
  // onto the same `purchaseTokenHash` and defeat any dedup/fraud-correlation use of that field.
  if (!payload.transactionId) {
    throw new AppStoreVerificationError('Apple transaction payload missing transactionId');
  }
  // Fix 2 (HIGH finding): without this check, ANY unrevoked Apple product -- a Consumable, a
  // Non-Consumable, a Non-Renewing Subscription, not just the app's actual auto-renewable Pro
  // subscription -- was mapped to permanent `pro`, with no real expiry tie-in (`expiresDate` is
  // only meaningful for auto-renewable subscriptions in the first place). A missing `type` is
  // rejected too, not treated as "assume subscription": a real StoreKit 2 transaction JWS always
  // carries this field, so its absence means either a malformed/forged payload or a payload shape
  // this function doesn't understand -- never a reason to grant Pro.
  if (payload.type !== AUTO_RENEWABLE_SUBSCRIPTION_TYPE) {
    throw new AppStoreVerificationError(
      `Apple transaction payload has non-subscription type "${payload.type ?? 'undefined'}" -- Pro is only granted for auto-renewable subscriptions`,
    );
  }
  const tier: EntitlementTier = payload.revocationDate ? 'free' : 'pro';
  // Fail closed: anything not positively Production (missing, Xcode, LocalTesting) is bounded.
  const environment = payload.environment === 'Production' ? 'Production' : 'Sandbox';
  const expiryTimeMillis =
    environment === 'Production'
      ? payload.expiresDate ?? null
      : Math.min(payload.expiresDate ?? Infinity, nowMillis + SANDBOX_ENTITLEMENT_MAX_AGE_MILLIS);
  const transactionId = payload.transactionId;
  return {
    tier,
    status: payload.revocationDate ? 'REVOKED' : 'ACTIVE',
    productId: payload.productId ?? null,
    // No base-plan concept in StoreKit 2 transactions -- Play-only field, kept null for shape
    // parity with `EntitlementDoc`.
    basePlanId: null,
    expiryTimeMillis,
    environment,
    // Auto-renew status lives in StoreKit 2's *renewal info* JWS, not the transaction JWS this
    // function maps -- `syncEntitlementIOS` only verifies the transaction, so this is unknown
    // rather than false-as-fact. Left `false` (not derived) until renewal-info verification is
    // added, same "not required now" scope as the `appAccountToken` hardening idea above.
    autoRenewing: false,
    purchaseTokenHash: sha256Hex(transactionId),
    lastVerifiedAt: nowMillis,
    source: 'sync-ios',
    transactionId,
  };
}

// ---------------------------------------------------------------------------------------------
// Port + orchestration shared with the real `syncEntitlementIOS` handler (mirrors
// `resolveEntitlement` in `billing.ts`).
// ---------------------------------------------------------------------------------------------

/** Port-agnostic App Store transaction verifier; the real implementation wraps
 * `@apple/app-store-server-library`'s `SignedDataVerifier` (Production-then-Sandbox fallback,
 * Apple root cert loading -- all SDK wiring, kept in `index.ts`). */
export interface AppStoreVerifier {
  verifyTransaction(signedTransaction: string): Promise<AppStoreTransactionPayload>;
}

/** Thrown by a real `AppStoreVerifier` for a JWS that fails Apple signature/chain/environment
 * verification -- i.e. an auth failure (maps to 401), never a transient one. Any other error the
 * verifier throws (e.g. Apple's revocation-check endpoint unreachable) is treated as transient by
 * `resolveIosEntitlement` (propagated, maps to 500) -- same 401-vs-500 split `handleRtdn` already
 * uses for Play. */
export class AppStoreVerificationError extends Error {}

/** Outcome of the atomic claim-and-write decision (Fix 1 + Fix 5's partial fix -- see
 * `IosEntitlementStore.claimAndWriteEntitlement` below):
 *  - `written`: the transactionId was unclaimed or already claimed by this same uid, and this
 *    transaction is not stale versus the previously stored entitlement for this uid (see
 *    `isStaleIosEntitlement`), or there was none.
 *  - `dropped-stale`: same claim outcome, but the entitlement is not written because either the
 *    incoming transaction expires earlier than the stored `sync-ios` one (an older transaction
 *    redelivered late), or -- on equal/unknown expiry -- this verification is not newer than the
 *    stored one (idempotent redelivery, same policy as `resolveEntitlement`'s for Play).
 *  - `claimed-by-other-uid`: the transactionId is already claimed by a DIFFERENT uid -- a replayed
 *    (e.g. leaked/intercepted) JWS being presented by an account that never made this purchase.
 *    Nothing is written; that other uid's entitlement is left untouched. */
export type ClaimAndWriteOutcome = 'written' | 'dropped-stale' | 'claimed-by-other-uid';

/** Pure staleness decision for an incoming iOS entitlement doc versus the stored one.
 *  1. Expiry ordering (transaction's own time): when the stored doc is `source === 'sync-ios'` and
 *     both stored and incoming `expiryTimeMillis` are numbers, an incoming expiry strictly earlier
 *     than the stored one is stale (an older transaction redelivered after a newer one), regardless
 *     of `lastVerifiedAt`. A strictly later expiry is never stale.
 *  0. Cross-environment: Production always supersedes Sandbox, Sandbox never replaces Production;
 *     the ordering below applies only within the same environment.
 *  2. Otherwise (equal expiry, non-`sync-ios` source, or a missing/null expiry on either side) the
 *     existing tie-breaker applies: stale when `incoming.lastVerifiedAt <= stored.lastVerifiedAt`.
 *     Equal expiry therefore lets a later revocation of the latest transaction win. */
export function isStaleIosEntitlement(
  stored: { source?: unknown; environment?: unknown; expiryTimeMillis?: unknown; lastVerifiedAt?: unknown } | undefined,
  incoming: AppStoreEntitlementDoc,
): boolean {
  if (!stored) return false;
  // Production always supersedes Sandbox (including a revoked Production doc), and Sandbox never
  // replaces Production. A stored doc without `environment` (legacy) is Production.
  const storedEnvironment = stored.environment === 'Sandbox' ? 'Sandbox' : 'Production';
  if (storedEnvironment !== incoming.environment) {
    return incoming.environment === 'Sandbox';
  }
  if (
    stored.source === 'sync-ios' &&
    typeof stored.expiryTimeMillis === 'number' &&
    typeof incoming.expiryTimeMillis === 'number'
  ) {
    if (incoming.expiryTimeMillis < stored.expiryTimeMillis) return true;
    if (incoming.expiryTimeMillis > stored.expiryTimeMillis) return false;
  }
  return typeof stored.lastVerifiedAt === 'number' && incoming.lastVerifiedAt <= stored.lastVerifiedAt;
}

/** Port-agnostic App Store entitlement store. Unlike Play's `EntitlementStore` (separate
 * `getLastVerifiedAt` read + `writeEntitlement` write, safe there because a purchase token/
 * subscriptionId always resolves to the SAME uid via Play's own `externalAccountIdentifiers`),
 * iOS trusts the caller's own uid (see this file's top-of-file doc comment) -- so the
 * transaction-claim ledger check and the entitlement write MUST happen as a single atomic
 * operation, or two concurrent requests for the same transactionId under different uids could both
 * pass a read-then-write race. The real implementation (`iosEntitlementStore()` in `index.ts`)
 * wraps this in one Firestore `runTransaction` over `iosTransactionClaims/{transactionId}` +
 * `users/{uid}/entitlements/current`. */
export interface IosEntitlementStore {
  claimAndWriteEntitlement(
    uid: string,
    transactionId: string,
    doc: AppStoreEntitlementDoc,
  ): Promise<ClaimAndWriteOutcome>;
}

export interface ResolveIosEntitlementResult {
  outcome: ClaimAndWriteOutcome | 'invalid';
  doc?: AppStoreEntitlementDoc;
}

/** Minimal decode port satisfied by `@apple/app-store-server-library`'s `SignedDataVerifier`. */
export interface AppStoreTransactionDecoder {
  verifyAndDecodeTransaction(signedTransaction: string): Promise<AppStoreTransactionPayload>;
}

/**
 * Production-first verification with a Sandbox fallback, per Apple's documented approach. Apple
 * App Review always purchases with sandbox-signed transactions against the production build, so
 * the fallback is unconditional; the risk (a free sandbox account minting Pro) is bounded by
 * `SANDBOX_ENTITLEMENT_MAX_AGE_MILLIS` in `toAppStoreEntitlement` instead of by a config flag.
 * Falls back ONLY when `isInvalidEnvironment(err)`; any other failure is mapped via `mapError`
 * (401 for auth failures, rethrown for transient ones) without trying Sandbox. With no Production
 * verifier (`null`: `IOS_APP_APPLE_ID` unset/malformed) it refuses outright. `IOS_APP_APPLE_ID` is
 * REQUIRED wherever this function runs: this repo has a single Firebase project for dev and prod,
 * so a Sandbox-only path would 401 every paying user whenever the variable is missing.
 * The result's `environment` is set from the verifier that succeeded.
 */
export function createEnvironmentFallbackVerifier(
  production: AppStoreTransactionDecoder | null,
  sandbox: AppStoreTransactionDecoder,
  opts: { isInvalidEnvironment(err: unknown): boolean; mapError(err: unknown): Error },
): AppStoreVerifier {
  return {
    async verifyTransaction(signedTransaction: string) {
      if (!production) {
        throw new AppStoreVerificationError(
          'Production App Store verification is not configured (IOS_APP_APPLE_ID is unset or malformed)',
        );
      }
      try {
        const payload = await production.verifyAndDecodeTransaction(signedTransaction);
        return { ...payload, environment: 'Production' };
      } catch (err) {
        if (!opts.isInvalidEnvironment(err)) throw opts.mapError(err);
      }
      try {
        const payload = await sandbox.verifyAndDecodeTransaction(signedTransaction);
        return { ...payload, environment: 'Sandbox' };
      } catch (err) {
        throw opts.mapError(err);
      }
    },
  };
}

/** True for the Apple library's INVALID_ENVIRONMENT failure (JWS valid but minted for the other
 * environment) -- the only failure that triggers the Sandbox fallback. */
export function isInvalidEnvironmentError(err: unknown): boolean {
  return err instanceof VerificationException && err.status === VerificationStatus.INVALID_ENVIRONMENT;
}

/** Maps a verification failure: RETRYABLE_VERIFICATION_FAILURE (Apple OCSP/revocation endpoint
 * unreachable) is transient and rethrown as-is (-> 500); every other `VerificationException` is an
 * auth failure (-> 401 via `AppStoreVerificationError`). */
export function mapVerificationError(err: unknown): Error {
  if (err instanceof VerificationException && err.status === VerificationStatus.RETRYABLE_VERIFICATION_FAILURE) {
    return err;
  }
  if (err instanceof VerificationException) {
    return new AppStoreVerificationError(err.message || 'Apple JWS verification failed');
  }
  return err instanceof Error ? err : new Error('unknown-error');
}

/** The real wiring: fallback verifier with the actual Apple SDK error classification. */
export function createAppStoreVerifier(
  production: AppStoreTransactionDecoder | null,
  sandbox: AppStoreTransactionDecoder,
): AppStoreVerifier {
  return createEnvironmentFallbackVerifier(production, sandbox, {
    isInvalidEnvironment: isInvalidEnvironmentError,
    mapError: mapVerificationError,
  });
}

/** Emits one structured line per Sandbox grant actually written, so sandbox grant volume can be
 * alerted on. `log` is `console.log`-shaped; no-op for Production or non-`written` outcomes. */
export function logSandboxGrant(
  log: (label: string, fields: Record<string, unknown>) => void,
  uid: string,
  doc: AppStoreEntitlementDoc,
  outcome: ClaimAndWriteOutcome,
): void {
  if (outcome !== 'written' || doc.environment !== 'Sandbox') return;
  log('[sandbox-entitlement-granted]', {
    environment: doc.environment,
    uid,
    productId: doc.productId,
    transactionId: doc.transactionId,
  });
}

/**
 * Verifies `signedTransaction` and atomically claims + writes the resulting entitlement via
 * `store.claimAndWriteEntitlement` (Fix 1: rejects a transactionId already claimed by a different
 * uid; Fix 5-adjacent: the freshness check happens inside that same atomic operation, not as a
 * separate racy read here).
 */
export async function resolveIosEntitlement(
  verifier: AppStoreVerifier,
  store: IosEntitlementStore,
  uid: string,
  signedTransaction: string,
  nowMillis: number,
): Promise<ResolveIosEntitlementResult> {
  let doc: AppStoreEntitlementDoc;
  let transactionId: string;
  try {
    const payload = await verifier.verifyTransaction(signedTransaction);
    doc = toAppStoreEntitlement(payload, nowMillis);
    // Guaranteed non-null: toAppStoreEntitlement throws AppStoreVerificationError above when
    // payload.transactionId is missing, so a successfully-returned doc always carries one.
    transactionId = doc.transactionId as string;
  } catch (err) {
    if (err instanceof AppStoreVerificationError) {
      return { outcome: 'invalid' };
    }
    throw err;
  }

  const outcome = await store.claimAndWriteEntitlement(uid, transactionId, doc);
  return { outcome, doc };
}

/**
 * Parses the `IOS_APP_APPLE_ID` env value. Returns a positive safe integer, or undefined when
 * unset/empty (silent) or malformed (logged, value not echoed) so callers fail closed.
 */
export function parseAppleAppId(raw: string | undefined): number | undefined {
  const trimmed = raw?.trim();
  if (!trimmed) return undefined;
  const parsed = /^\d+$/.test(trimmed) ? Number(trimmed) : NaN;
  if (!Number.isSafeInteger(parsed) || parsed <= 0) {
    console.error('[appStoreVerifier] IOS_APP_APPLE_ID is set but malformed (expected a positive integer); treating as unset.');
    return undefined;
  }
  return parsed;
}
