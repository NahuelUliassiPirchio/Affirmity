import { describe, expect, it, vi } from 'vitest';

import {
  AppStoreVerificationError,
  isSandboxEntitlementsAllowed,
  resolveIosEntitlement,
  toAppStoreEntitlement,
  type AppStoreEntitlementDoc,
  type AppStoreTransactionPayload,
  type AppStoreVerifier,
  type IosEntitlementStore,
} from '../src/appStoreBilling';

// Spec: state->tier mapping for App Store transactions -- only `revocationDate` flips it to free;
// an unrevoked-but-expired transaction is the caller's job to resolve via `resolveTier`.
describe('toAppStoreEntitlement', () => {
  const activePayload: AppStoreTransactionPayload = {
    transactionId: 'txn-1',
    productId: 'pro_monthly',
    expiresDate: Date.parse('2026-09-01T00:00:00.000Z'),
    type: 'Auto-Renewable Subscription',
  };

  it('maps an active (unrevoked) transaction to pro', () => {
    const doc = toAppStoreEntitlement(activePayload, 1000);
    expect(doc.tier).toBe('pro');
    expect(doc.status).toBe('ACTIVE');
  });

  it('maps a revoked/refunded transaction to free', () => {
    const doc = toAppStoreEntitlement({ ...activePayload, revocationDate: 1500 }, 1000);
    expect(doc.tier).toBe('free');
    expect(doc.status).toBe('REVOKED');
  });

  it('maps an unrevoked but time-expired transaction to pro (resolveTier is the reader\'s job)', () => {
    const expiredPayload: AppStoreTransactionPayload = {
      transactionId: 'txn-2',
      productId: 'pro_monthly',
      expiresDate: Date.parse('2020-01-01T00:00:00.000Z'),
      type: 'Auto-Renewable Subscription',
    };
    const doc = toAppStoreEntitlement(expiredPayload, Date.parse('2026-09-01T00:00:00.000Z'));
    expect(doc.tier).toBe('pro');
    expect(doc.expiryTimeMillis).toBe(expiredPayload.expiresDate);
  });

  it('carries productId, expiryTimeMillis, transactionId and source', () => {
    const doc = toAppStoreEntitlement(activePayload, 1000);
    expect(doc.productId).toBe('pro_monthly');
    expect(doc.expiryTimeMillis).toBe(activePayload.expiresDate);
    expect(doc.transactionId).toBe('txn-1');
    expect(doc.source).toBe('sync-ios');
    expect(doc.lastVerifiedAt).toBe(1000);
  });

  it('stores a hash of the transaction id in purchaseTokenHash (shape parity with Play), never the raw id', () => {
    const doc = toAppStoreEntitlement(activePayload, 1000);
    expect(doc.purchaseTokenHash).not.toContain('txn-1');
    expect(doc.purchaseTokenHash).toMatch(/^[a-f0-9]{64}$/);
  });

  it('handles a payload missing productId without throwing', () => {
    const doc = toAppStoreEntitlement(
      { transactionId: 'txn-1', type: 'Auto-Renewable Subscription' },
      1000,
    );
    expect(doc.productId).toBeNull();
    expect(doc.transactionId).toBe('txn-1');
    expect(doc.tier).toBe('pro');
  });

  it('rejects a verified payload missing transactionId instead of hashing an empty string', () => {
    expect(() =>
      toAppStoreEntitlement({ type: 'Auto-Renewable Subscription' }, 1000),
    ).toThrow('missing transactionId');
  });

  // Fix 2 (CRITICAL/HIGH finding): without a product-type check, ANY unrevoked Apple product --
  // a Consumable, a Non-Consumable, a Non-Renewing Subscription -- was mapped to permanent `pro`,
  // with no real expiry tie-in. Only an Auto-Renewable Subscription transaction should ever grant
  // Pro.
  describe('product-type filtering (Fix 2)', () => {
    it('rejects a Non-Consumable product instead of granting permanent pro', () => {
      expect(() =>
        toAppStoreEntitlement({ ...activePayload, type: 'Non-Consumable' }, 1000),
      ).toThrow(AppStoreVerificationError);
    });

    it('rejects a Consumable product', () => {
      expect(() =>
        toAppStoreEntitlement({ ...activePayload, type: 'Consumable' }, 1000),
      ).toThrow(AppStoreVerificationError);
    });

    it('rejects a Non-Renewing Subscription product', () => {
      expect(() =>
        toAppStoreEntitlement({ ...activePayload, type: 'Non-Renewing Subscription' }, 1000),
      ).toThrow(AppStoreVerificationError);
    });

    it('rejects a payload with no type at all -- a real StoreKit2 JWS always carries one', () => {
      const { type, ...withoutType } = activePayload;
      expect(() => toAppStoreEntitlement(withoutType, 1000)).toThrow(AppStoreVerificationError);
    });

    it('accepts an Auto-Renewable Subscription product', () => {
      const doc = toAppStoreEntitlement({ ...activePayload, type: 'Auto-Renewable Subscription' }, 1000);
      expect(doc.tier).toBe('pro');
    });
  });
});

// Fix 3: gate Sandbox-verification fallback behind an explicit env var, default off, so a free
// Apple Sandbox account can never mint a real Production Pro entitlement.
describe('isSandboxEntitlementsAllowed (Fix 3)', () => {
  it('is disabled by default (unset env var)', () => {
    expect(isSandboxEntitlementsAllowed(undefined)).toBe(false);
  });

  it('is disabled for any value other than the literal "true"', () => {
    expect(isSandboxEntitlementsAllowed('')).toBe(false);
    expect(isSandboxEntitlementsAllowed('1')).toBe(false);
    expect(isSandboxEntitlementsAllowed('TRUE')).toBe(false);
    expect(isSandboxEntitlementsAllowed('yes')).toBe(false);
  });

  it('is enabled only for the exact literal "true"', () => {
    expect(isSandboxEntitlementsAllowed('true')).toBe(true);
  });
});

describe('resolveIosEntitlement', () => {
  const UID = 'uid-1';
  const OTHER_UID = 'uid-2';
  const payload: AppStoreTransactionPayload = {
    transactionId: 'txn-1',
    productId: 'pro_monthly',
    expiresDate: 9999,
    type: 'Auto-Renewable Subscription',
  };

  /**
   * In-memory fake standing in for the real Firestore-transaction-backed `IosEntitlementStore`
   * (`iosEntitlementStore()` in `index.ts`) -- models exactly the same atomic read-check-write
   * contract: a `transactionId` claimed by one uid can never be silently reclaimed by another
   * (Fix 1), and the freshness check happens in the same atomic step as the write (Fix 5's partial
   * fix), never as a separate read a caller could race.
   */
  function fakeIosEntitlementStore(): IosEntitlementStore & {
    claims: Map<string, string>;
    entitlements: Map<string, AppStoreEntitlementDoc>;
  } {
    const claims = new Map<string, string>();
    const entitlements = new Map<string, AppStoreEntitlementDoc>();
    return {
      claims,
      entitlements,
      async claimAndWriteEntitlement(uid, transactionId, doc) {
        const claimant = claims.get(transactionId);
        if (claimant && claimant !== uid) {
          return 'claimed-by-other-uid';
        }
        const existing = entitlements.get(uid);
        const isStale = existing !== undefined && doc.lastVerifiedAt <= existing.lastVerifiedAt;
        if (!claimant) {
          claims.set(transactionId, uid);
        }
        if (isStale) {
          return 'dropped-stale';
        }
        entitlements.set(uid, doc);
        return 'written';
      },
    };
  }

  function verifierReturning(result: AppStoreTransactionPayload): AppStoreVerifier {
    return { verifyTransaction: vi.fn(async () => result) };
  }

  it('writes the resolved entitlement when this verification is newer than the stored one', async () => {
    const store = fakeIosEntitlementStore();
    const verifier = verifierReturning(payload);

    const result = await resolveIosEntitlement(verifier, store, UID, 'jws-1', 2000);

    expect(result.outcome).toBe('written');
    expect(store.entitlements.get(UID)).toMatchObject({ tier: 'pro' });
  });

  // Spec: idempotency -- a stale re-verification whose lastVerifiedAt is older than the stored one
  // is dropped, mirroring `resolveEntitlement`'s policy for Play.
  it('drops a stale re-verification without writing', async () => {
    const store = fakeIosEntitlementStore();
    store.entitlements.set(UID, { ...toAppStoreEntitlement(payload, 5000) });
    const verifier = verifierReturning(payload);

    const result = await resolveIosEntitlement(verifier, store, UID, 'jws-1', 2000);

    expect(result.outcome).toBe('dropped-stale');
    expect(store.entitlements.get(UID)?.lastVerifiedAt).toBe(5000);
  });

  it('writes when there is no previously stored lastVerifiedAt', async () => {
    const store = fakeIosEntitlementStore();
    const verifier = verifierReturning(payload);

    const result = await resolveIosEntitlement(verifier, store, UID, 'jws-1', 2000);

    expect(result.outcome).toBe('written');
  });

  // Spec: a JWS that fails Apple signature verification is an auth failure, not a server error --
  // the caller (`index.ts`) maps this outcome to 401, never 500.
  it('reports invalid and never writes when the verifier rejects the JWS as unverifiable', async () => {
    const store = fakeIosEntitlementStore();
    const verifier: AppStoreVerifier = {
      verifyTransaction: vi.fn(async () => {
        throw new AppStoreVerificationError('bad signature');
      }),
    };

    const result = await resolveIosEntitlement(verifier, store, UID, 'garbage', 2000);

    expect(result.outcome).toBe('invalid');
    expect(store.entitlements.size).toBe(0);
  });

  // Spec: a genuine transient failure (e.g. Apple's revocation-check endpoint unreachable) is not
  // an AppStoreVerificationError -- it propagates so the caller maps it to 500, mirroring
  // `handleRtdn`'s 500-on-transient-Play-API-failure behavior.
  it('propagates a non-verification error instead of reporting invalid', async () => {
    const store = fakeIosEntitlementStore();
    const verifier: AppStoreVerifier = {
      verifyTransaction: vi.fn(async () => {
        throw new Error('ECONNRESET');
      }),
    };

    await expect(resolveIosEntitlement(verifier, store, UID, 'jws-1', 2000)).rejects.toThrow('ECONNRESET');
    expect(store.entitlements.size).toBe(0);
  });

  // Fix 1 (CRITICAL finding): cross-account transaction replay. A bearer JWS (e.g. leaked/
  // intercepted) previously granted Pro to WHATEVER Firebase uid presented it, because the write
  // target came only from the caller's own ID token with no binding to the transaction itself.
  describe('cross-account transaction replay (Fix 1)', () => {
    it('rejects a transactionId already claimed by a DIFFERENT uid instead of overwriting their entitlement', async () => {
      const store = fakeIosEntitlementStore();
      const verifier = verifierReturning(payload);

      // uid-1 legitimately claims txn-1 first.
      const first = await resolveIosEntitlement(verifier, store, UID, 'jws-1', 1000);
      expect(first.outcome).toBe('written');

      // uid-2 replays the same signed transaction (e.g. a leaked JWS) authenticated as itself.
      const replay = await resolveIosEntitlement(verifier, store, OTHER_UID, 'jws-1', 2000);

      expect(replay.outcome).toBe('claimed-by-other-uid');
      // uid-2 must never be granted an entitlement from someone else's transaction.
      expect(store.entitlements.has(OTHER_UID)).toBe(false);
      // uid-1's original entitlement must be untouched.
      expect(store.entitlements.get(UID)).toMatchObject({ tier: 'pro' });
    });

    it('allows the SAME uid to legitimately re-sync the same transaction (idempotent re-sync)', async () => {
      const store = fakeIosEntitlementStore();
      const verifier = verifierReturning(payload);

      const first = await resolveIosEntitlement(verifier, store, UID, 'jws-1', 1000);
      expect(first.outcome).toBe('written');

      const resync = await resolveIosEntitlement(verifier, store, UID, 'jws-1', 2000);

      expect(resync.outcome).toBe('written');
      expect(store.entitlements.get(UID)?.lastVerifiedAt).toBe(2000);
    });
  });
});
