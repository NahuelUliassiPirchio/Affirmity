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
}

/** The only `AppStoreTransactionPayload.type` value this app grants Pro for -- see Fix 2 doc
 * comment on `toAppStoreEntitlement` below. Mirrors
 * `@apple/app-store-server-library`'s `Type.AUTO_RENEWABLE_SUBSCRIPTION`. */
const AUTO_RENEWABLE_SUBSCRIPTION_TYPE = 'Auto-Renewable Subscription';

export type AppStoreEntitlementDoc = EntitlementDoc & {
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
  const transactionId = payload.transactionId;
  return {
    tier,
    status: payload.revocationDate ? 'REVOKED' : 'ACTIVE',
    productId: payload.productId ?? null,
    // No base-plan concept in StoreKit 2 transactions -- Play-only field, kept null for shape
    // parity with `EntitlementDoc`.
    basePlanId: null,
    expiryTimeMillis: payload.expiresDate ?? null,
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
 *    verification is newer than (or there was no) previously stored entitlement for this uid.
 *  - `dropped-stale`: same claim outcome, but this verification is not newer -- idempotent
 *    redelivery, identical policy to `resolveEntitlement`'s for Play.
 *  - `claimed-by-other-uid`: the transactionId is already claimed by a DIFFERENT uid -- a replayed
 *    (e.g. leaked/intercepted) JWS being presented by an account that never made this purchase.
 *    Nothing is written; that other uid's entitlement is left untouched. */
export type ClaimAndWriteOutcome = 'written' | 'dropped-stale' | 'claimed-by-other-uid';

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

/** Fix 3 (HIGH finding): parses the `IOS_ALLOW_SANDBOX_ENTITLEMENTS` env var (same
 * `process.env.X ?? default` convention as `IOS_BUNDLE_ID`/`IOS_APP_APPLE_ID` in `index.ts`).
 * Default OFF -- only the exact literal `"true"` enables Sandbox-verification fallback. Without
 * this gate, anyone can create a free Apple Sandbox account, generate a Sandbox transaction, and
 * get a real Production Pro entitlement for zero payment. This should stay enabled in dev/staging
 * Firebase projects (where there is no shipped Production build yet) and be turned OFF once the
 * app is live in production. */
export function isSandboxEntitlementsAllowed(envValue: string | undefined): boolean {
  return envValue === 'true';
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
