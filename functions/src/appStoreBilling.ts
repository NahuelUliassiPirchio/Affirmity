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

import type { EntitlementDoc, EntitlementStore, EntitlementTier } from './billing';

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
}

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
  const tier: EntitlementTier = payload.revocationDate ? 'free' : 'pro';
  const transactionId = payload.transactionId ?? null;
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
    purchaseTokenHash: sha256Hex(transactionId ?? ''),
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

export interface ResolveIosEntitlementResult {
  outcome: 'written' | 'dropped-stale' | 'invalid';
  doc?: AppStoreEntitlementDoc;
}

/**
 * Verifies `signedTransaction` and writes the resulting entitlement, unless it is stale
 * (idempotency: last-write-wins keyed by `lastVerifiedAt`, identical policy to
 * `resolveEntitlement`'s for Play -- same store, same `users/{uid}/entitlements/current` doc).
 */
export async function resolveIosEntitlement(
  verifier: AppStoreVerifier,
  store: EntitlementStore,
  uid: string,
  signedTransaction: string,
  nowMillis: number,
): Promise<ResolveIosEntitlementResult> {
  let payload: AppStoreTransactionPayload;
  try {
    payload = await verifier.verifyTransaction(signedTransaction);
  } catch (err) {
    if (err instanceof AppStoreVerificationError) {
      return { outcome: 'invalid' };
    }
    throw err;
  }

  const doc = toAppStoreEntitlement(payload, nowMillis);
  const lastVerifiedAt = await store.getLastVerifiedAt(uid);
  if (lastVerifiedAt !== null && doc.lastVerifiedAt <= lastVerifiedAt) {
    return { outcome: 'dropped-stale', doc };
  }

  await store.writeEntitlement(uid, doc);
  return { outcome: 'written', doc };
}
