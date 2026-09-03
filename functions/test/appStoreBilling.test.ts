import { describe, expect, it, vi } from 'vitest';

import {
  AppStoreVerificationError,
  resolveIosEntitlement,
  toAppStoreEntitlement,
  type AppStoreTransactionPayload,
  type AppStoreVerifier,
} from '../src/appStoreBilling';
import type { EntitlementStore } from '../src/billing';

// Spec: state->tier mapping for App Store transactions -- only `revocationDate` flips it to free;
// an unrevoked-but-expired transaction is the caller's job to resolve via `resolveTier`.
describe('toAppStoreEntitlement', () => {
  const activePayload: AppStoreTransactionPayload = {
    transactionId: 'txn-1',
    productId: 'pro_monthly',
    expiresDate: Date.parse('2026-09-01T00:00:00.000Z'),
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

  it('handles a payload missing productId/transactionId without throwing', () => {
    const doc = toAppStoreEntitlement({}, 1000);
    expect(doc.productId).toBeNull();
    expect(doc.transactionId).toBeNull();
    expect(doc.tier).toBe('pro');
  });
});

describe('resolveIosEntitlement', () => {
  const UID = 'uid-1';
  const payload: AppStoreTransactionPayload = {
    transactionId: 'txn-1',
    productId: 'pro_monthly',
    expiresDate: 9999,
  };

  function storeWithLastVerifiedAt(lastVerifiedAt: number | null): EntitlementStore {
    return {
      getLastVerifiedAt: vi.fn(async () => lastVerifiedAt),
      writeEntitlement: vi.fn(async () => undefined),
    };
  }

  function verifierReturning(result: AppStoreTransactionPayload): AppStoreVerifier {
    return { verifyTransaction: vi.fn(async () => result) };
  }

  it('writes the resolved entitlement when this verification is newer than the stored one', async () => {
    const store = storeWithLastVerifiedAt(1000);
    const verifier = verifierReturning(payload);

    const result = await resolveIosEntitlement(verifier, store, UID, 'jws-1', 2000);

    expect(result.outcome).toBe('written');
    expect(store.writeEntitlement).toHaveBeenCalledWith(UID, expect.objectContaining({ tier: 'pro' }));
  });

  // Spec: idempotency -- a stale re-verification whose lastVerifiedAt is older than the stored one
  // is dropped, mirroring `resolveEntitlement`'s policy for Play.
  it('drops a stale re-verification without writing', async () => {
    const store = storeWithLastVerifiedAt(5000);
    const verifier = verifierReturning(payload);

    const result = await resolveIosEntitlement(verifier, store, UID, 'jws-1', 2000);

    expect(result.outcome).toBe('dropped-stale');
    expect(store.writeEntitlement).not.toHaveBeenCalled();
  });

  it('writes when there is no previously stored lastVerifiedAt', async () => {
    const store = storeWithLastVerifiedAt(null);
    const verifier = verifierReturning(payload);

    const result = await resolveIosEntitlement(verifier, store, UID, 'jws-1', 2000);

    expect(result.outcome).toBe('written');
  });

  // Spec: a JWS that fails Apple signature verification is an auth failure, not a server error --
  // the caller (`index.ts`) maps this outcome to 401, never 500.
  it('reports invalid and never writes when the verifier rejects the JWS as unverifiable', async () => {
    const store = storeWithLastVerifiedAt(null);
    const verifier: AppStoreVerifier = {
      verifyTransaction: vi.fn(async () => {
        throw new AppStoreVerificationError('bad signature');
      }),
    };

    const result = await resolveIosEntitlement(verifier, store, UID, 'garbage', 2000);

    expect(result.outcome).toBe('invalid');
    expect(store.writeEntitlement).not.toHaveBeenCalled();
  });

  // Spec: a genuine transient failure (e.g. Apple's revocation-check endpoint unreachable) is not
  // an AppStoreVerificationError -- it propagates so the caller maps it to 500, mirroring
  // `handleRtdn`'s 500-on-transient-Play-API-failure behavior.
  it('propagates a non-verification error instead of reporting invalid', async () => {
    const store = storeWithLastVerifiedAt(null);
    const verifier: AppStoreVerifier = {
      verifyTransaction: vi.fn(async () => {
        throw new Error('ECONNRESET');
      }),
    };

    await expect(resolveIosEntitlement(verifier, store, UID, 'jws-1', 2000)).rejects.toThrow('ECONNRESET');
    expect(store.writeEntitlement).not.toHaveBeenCalled();
  });
});
