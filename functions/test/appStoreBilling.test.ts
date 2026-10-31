import { VerificationException, VerificationStatus } from '@apple/app-store-server-library';
import { describe, expect, it, vi } from 'vitest';

import {
  AppStoreVerificationError,
  SANDBOX_ENTITLEMENT_MAX_AGE_MILLIS,
  isStaleIosEntitlement,
  parseAppleAppId,
  createAppStoreVerifier,
  createEnvironmentFallbackVerifier,
  logSandboxGrant,
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
    environment: 'Production',
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

// Sandbox-signed transactions (App Review, TestFlight, Xcode) are accepted but bounded: a free
// sandbox account must never become a long-lived Pro.
describe('environment handling and sandbox bounds', () => {
  const NOW = Date.parse('2026-10-31T00:00:00.000Z');
  const HOUR = 60 * 60 * 1000;
  const base: AppStoreTransactionPayload = {
    transactionId: 'txn-env',
    productId: 'pro_monthly',
    type: 'Auto-Renewable Subscription',
  };

  it('records Production and leaves expiry untouched for a Production transaction', () => {
    const expiresDate = NOW + 30 * 24 * HOUR;
    const doc = toAppStoreEntitlement({ ...base, environment: 'Production', expiresDate }, NOW);
    expect(doc.environment).toBe('Production');
    expect(doc.expiryTimeMillis).toBe(expiresDate);
  });

  it('records Sandbox and keeps the transaction expiresDate when it is sooner than the hard cap', () => {
    const expiresDate = NOW + 5 * 60 * 1000;
    const doc = toAppStoreEntitlement({ ...base, environment: 'Sandbox', expiresDate }, NOW);
    expect(doc.environment).toBe('Sandbox');
    expect(doc.expiryTimeMillis).toBe(expiresDate);
  });

  it('caps a Sandbox expiry at the hard server-side limit when expiresDate is later', () => {
    const doc = toAppStoreEntitlement(
      { ...base, environment: 'Sandbox', expiresDate: NOW + 30 * 24 * HOUR },
      NOW,
    );
    expect(SANDBOX_ENTITLEMENT_MAX_AGE_MILLIS).toBeLessThanOrEqual(24 * HOUR);
    expect(doc.expiryTimeMillis).toBe(NOW + SANDBOX_ENTITLEMENT_MAX_AGE_MILLIS);
  });

  it('caps a Sandbox transaction with no expiresDate instead of leaving it open-ended', () => {
    const doc = toAppStoreEntitlement({ ...base, environment: 'Sandbox' }, NOW);
    expect(doc.expiryTimeMillis).toBe(NOW + SANDBOX_ENTITLEMENT_MAX_AGE_MILLIS);
  });

  it('fails closed: an unknown or missing environment is treated as Sandbox', () => {
    const expiresDate = NOW + 30 * 24 * HOUR;
    for (const environment of [undefined, 'Xcode', 'LocalTesting']) {
      const doc = toAppStoreEntitlement({ ...base, environment, expiresDate }, NOW);
      expect(doc.environment).toBe('Sandbox');
      expect(doc.expiryTimeMillis).toBe(NOW + SANDBOX_ENTITLEMENT_MAX_AGE_MILLIS);
    }
  });

  it('writes a sandbox JWS end to end with environment Sandbox', async () => {
    const written: AppStoreEntitlementDoc[] = [];
    const store: IosEntitlementStore = {
      async claimAndWriteEntitlement(_uid, _txn, doc) {
        written.push(doc);
        return 'written';
      },
    };
    const verifier: AppStoreVerifier = {
      verifyTransaction: vi.fn(async () => ({ ...base, environment: 'Sandbox', expiresDate: NOW + 3 * 60 * 1000 })),
    };
    const result = await resolveIosEntitlement(verifier, store, 'uid-1', 'jws', NOW);
    expect(result.outcome).toBe('written');
    expect(written[0]).toMatchObject({ environment: 'Sandbox', expiryTimeMillis: NOW + 3 * 60 * 1000 });
  });
});


// Production-first verification with a Sandbox fallback ONLY on INVALID_ENVIRONMENT (Apple's
// documented approach; App Review always buys with sandbox-signed transactions).
describe('createEnvironmentFallbackVerifier', () => {
  class InvalidEnvironment extends Error {}
  class BadSignature extends Error {}
  const payload: AppStoreTransactionPayload = {
    transactionId: 'txn-f',
    type: 'Auto-Renewable Subscription',
    expiresDate: 1,
  };
  const opts = {
    isInvalidEnvironment: (err: unknown) => err instanceof InvalidEnvironment,
    mapError: (err: unknown) => (err instanceof BadSignature ? new AppStoreVerificationError('bad') : (err as Error)),
  };

  it('uses Production first and tags the payload Production', async () => {
    const sandbox = { verifyAndDecodeTransaction: vi.fn() };
    const verifier = createEnvironmentFallbackVerifier(
      { verifyAndDecodeTransaction: vi.fn(async () => payload) },
      sandbox,
      opts,
    );
    await expect(verifier.verifyTransaction('jws')).resolves.toMatchObject({ environment: 'Production' });
    expect(sandbox.verifyAndDecodeTransaction).not.toHaveBeenCalled();
  });

  it('falls back to Sandbox on INVALID_ENVIRONMENT and tags the payload Sandbox', async () => {
    const verifier = createEnvironmentFallbackVerifier(
      { verifyAndDecodeTransaction: vi.fn(async () => { throw new InvalidEnvironment(); }) },
      { verifyAndDecodeTransaction: vi.fn(async () => ({ ...payload, environment: 'Production' })) },
      opts,
    );
    // The tag comes from the verifier that succeeded, never from the (client-controlled-looking) payload.
    await expect(verifier.verifyTransaction('jws')).resolves.toMatchObject({ environment: 'Sandbox' });
  });

  it('does NOT fall back to Sandbox for a signature/chain failure', async () => {
    const sandbox = { verifyAndDecodeTransaction: vi.fn(async () => payload) };
    const verifier = createEnvironmentFallbackVerifier(
      { verifyAndDecodeTransaction: vi.fn(async () => { throw new BadSignature(); }) },
      sandbox,
      opts,
    );
    await expect(verifier.verifyTransaction('jws')).rejects.toBeInstanceOf(AppStoreVerificationError);
    expect(sandbox.verifyAndDecodeTransaction).not.toHaveBeenCalled();
  });

  it('maps a Sandbox-side verification failure after fallback', async () => {
    const verifier = createEnvironmentFallbackVerifier(
      { verifyAndDecodeTransaction: vi.fn(async () => { throw new InvalidEnvironment(); }) },
      { verifyAndDecodeTransaction: vi.fn(async () => { throw new BadSignature(); }) },
      opts,
    );
    await expect(verifier.verifyTransaction('jws')).rejects.toBeInstanceOf(AppStoreVerificationError);
  });

  it('refuses (AppStoreVerificationError) when no Production verifier is configured, never trying Sandbox', async () => {
    const sandbox = { verifyAndDecodeTransaction: vi.fn(async () => payload) };
    const verifier = createEnvironmentFallbackVerifier(null, sandbox, opts);
    await expect(verifier.verifyTransaction('jws')).rejects.toBeInstanceOf(AppStoreVerificationError);
    await expect(verifier.verifyTransaction('jws')).rejects.toThrow('IOS_APP_APPLE_ID');
    expect(sandbox.verifyAndDecodeTransaction).not.toHaveBeenCalled();
  });
});

// Real SDK boundary: the actual VerificationException / VerificationStatus from the Apple library.
describe('createAppStoreVerifier (real SDK error types)', () => {
  const payload: AppStoreTransactionPayload = { transactionId: 'txn-s', type: 'Auto-Renewable Subscription' };
  const throwing = (status: VerificationStatus) => ({
    verifyAndDecodeTransaction: vi.fn(async () => {
      throw new VerificationException(status);
    }),
  });
  const ok = () => ({ verifyAndDecodeTransaction: vi.fn(async () => payload) });

  it('falls back to Sandbox on a real INVALID_ENVIRONMENT VerificationException', async () => {
    const verifier = createAppStoreVerifier(throwing(VerificationStatus.INVALID_ENVIRONMENT), ok());
    await expect(verifier.verifyTransaction('jws')).resolves.toMatchObject({ environment: 'Sandbox' });
  });

  it('does not fall back on a real VERIFICATION_FAILURE and maps it to AppStoreVerificationError', async () => {
    const sandbox = ok();
    const verifier = createAppStoreVerifier(throwing(VerificationStatus.VERIFICATION_FAILURE), sandbox);
    await expect(verifier.verifyTransaction('jws')).rejects.toBeInstanceOf(AppStoreVerificationError);
    expect(sandbox.verifyAndDecodeTransaction).not.toHaveBeenCalled();
  });

  it('does not fall back on RETRYABLE_VERIFICATION_FAILURE and does not map it to a 401', async () => {
    const sandbox = ok();
    const verifier = createAppStoreVerifier(throwing(VerificationStatus.RETRYABLE_VERIFICATION_FAILURE), sandbox);
    const err = await verifier.verifyTransaction('jws').catch((e: unknown) => e);
    expect(err).toBeInstanceOf(VerificationException);
    expect(err).not.toBeInstanceOf(AppStoreVerificationError);
    expect(sandbox.verifyAndDecodeTransaction).not.toHaveBeenCalled();
  });

  it('refuses when no Production verifier is configured', async () => {
    const verifier = createAppStoreVerifier(null, ok());
    await expect(verifier.verifyTransaction('jws')).rejects.toBeInstanceOf(AppStoreVerificationError);
  });
});

describe('logSandboxGrant', () => {
  const doc = (environment: 'Production' | 'Sandbox') => ({
    ...toAppStoreEntitlement(
      { transactionId: 'txn-l', productId: 'pro_monthly', type: 'Auto-Renewable Subscription', environment },
      1000,
    ),
  });

  it('logs a structured line for a Sandbox written outcome', () => {
    const log = vi.fn();
    logSandboxGrant(log, 'uid-1', doc('Sandbox'), 'written');
    expect(log).toHaveBeenCalledTimes(1);
    const [label, fields] = log.mock.calls[0];
    expect(label).toBe('[sandbox-entitlement-granted]');
    expect(fields).toEqual({ environment: 'Sandbox', uid: 'uid-1', productId: 'pro_monthly', transactionId: 'txn-l' });
  });

  it('does not log for Production or non-written outcomes', () => {
    const log = vi.fn();
    logSandboxGrant(log, 'uid-1', doc('Production'), 'written');
    logSandboxGrant(log, 'uid-1', doc('Sandbox'), 'dropped-stale');
    logSandboxGrant(log, 'uid-1', doc('Sandbox'), 'claimed-by-other-uid');
    expect(log).not.toHaveBeenCalled();
  });
});

describe('isStaleIosEntitlement across environments', () => {
  const make = (environment: 'Production' | 'Sandbox', over: Partial<AppStoreEntitlementDoc> = {}) => ({
    ...toAppStoreEntitlement(
      { transactionId: 't', productId: 'p', expiresDate: 1000, type: 'Auto-Renewable Subscription', environment },
      500,
    ),
    ...over,
  });
  const storedProd = { source: 'sync-ios', environment: 'Production', expiryTimeMillis: 9_000_000, lastVerifiedAt: 900 };
  const storedSandbox = { source: 'sync-ios', environment: 'Sandbox', expiryTimeMillis: 9_000_000, lastVerifiedAt: 900 };

  it('incoming Production replaces stored Sandbox even with earlier expiry and older verification', () => {
    expect(isStaleIosEntitlement(storedSandbox, make('Production', { expiryTimeMillis: 1000, lastVerifiedAt: 1 }))).toBe(false);
  });

  it('incoming revoked Production (tier free) replaces stored Sandbox', () => {
    const revoked = { ...make('Production', { expiryTimeMillis: 1000, lastVerifiedAt: 1 }), tier: 'free' as const, status: 'REVOKED' as const };
    expect(isStaleIosEntitlement(storedSandbox, revoked)).toBe(false);
  });

  it('incoming Sandbox never replaces stored Production, even with later expiry and newer verification', () => {
    expect(isStaleIosEntitlement(storedProd, make('Sandbox', { expiryTimeMillis: 99_000_000, lastVerifiedAt: 99_999 }))).toBe(true);
  });

  it('orders by expiry/lastVerifiedAt within Sandbox', () => {
    expect(isStaleIosEntitlement(storedSandbox, make('Sandbox', { expiryTimeMillis: 1000, lastVerifiedAt: 1 }))).toBe(true);
    expect(isStaleIosEntitlement(storedSandbox, make('Sandbox', { expiryTimeMillis: 99_000_000, lastVerifiedAt: 1 }))).toBe(false);
  });

  it('treats a legacy stored doc without environment as Production (incoming Sandbox is stale)', () => {
    const legacy = { source: 'sync-ios', expiryTimeMillis: 1000, lastVerifiedAt: 100 };
    expect(isStaleIosEntitlement(legacy, make('Sandbox', { expiryTimeMillis: 5000, lastVerifiedAt: 999 }))).toBe(true);
  });

  it('incoming Production against a legacy stored doc still orders by expiry', () => {
    const legacy = { source: 'sync-ios', expiryTimeMillis: 2000, lastVerifiedAt: 100 };
    expect(isStaleIosEntitlement(legacy, make('Production', { expiryTimeMillis: 1000, lastVerifiedAt: 999 }))).toBe(true);
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
    environment: 'Production',
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

describe('parseAppleAppId', () => {
  it('parses a positive integer string', () => {
    expect(parseAppleAppId('1234567890')).toBe(1234567890);
    expect(parseAppleAppId(' 42 ')).toBe(42);
  });

  it.each([undefined, '', '   ', 'abc', 'NaN', '-5', '0', '1.5', '12abc', '1e3', 'Infinity', '9007199254740993'])(
    'returns undefined for %j',
    (raw) => {
      const spy = vi.spyOn(console, 'error').mockImplementation(() => {});
      expect(parseAppleAppId(raw as string | undefined)).toBeUndefined();
      spy.mockRestore();
    },
  );

  it('logs an error only when the value is set but malformed', () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {});
    parseAppleAppId(undefined);
    parseAppleAppId('');
    expect(spy).not.toHaveBeenCalled();
    parseAppleAppId('abc');
    expect(spy).toHaveBeenCalledTimes(1);
    expect(String(spy.mock.calls[0][0])).toContain('malformed');
    expect(String(spy.mock.calls[0][0])).not.toContain('abc');
    spy.mockRestore();
  });
});

// Ordering rule on the transaction's own time: an older App Store transaction redelivered after a
// newer one must not overwrite it, even though its server-side `lastVerifiedAt` is later.
describe('isStaleIosEntitlement', () => {
  const stored = { source: 'sync-ios', expiryTimeMillis: 2000, lastVerifiedAt: 100 };
  const incoming = (overrides: Partial<AppStoreEntitlementDoc> = {}): AppStoreEntitlementDoc => ({
    ...toAppStoreEntitlement(
      {
        transactionId: 'txn-x',
        productId: 'pro_monthly',
        expiresDate: 2000,
        type: 'Auto-Renewable Subscription',
        environment: 'Production',
      },
      200,
    ),
    ...overrides,
  });

  it('is stale when the incoming expiry is older than the stored sync-ios expiry, even if verified later', () => {
    expect(isStaleIosEntitlement(stored, incoming({ expiryTimeMillis: 1000, lastVerifiedAt: 999 }))).toBe(true);
  });

  it('falls back to lastVerifiedAt on equal expiry: older verification is stale', () => {
    expect(isStaleIosEntitlement(stored, incoming({ expiryTimeMillis: 2000, lastVerifiedAt: 50 }))).toBe(true);
  });

  it('falls back to lastVerifiedAt on equal expiry: equal verification is stale', () => {
    expect(isStaleIosEntitlement(stored, incoming({ expiryTimeMillis: 2000, lastVerifiedAt: 100 }))).toBe(true);
  });

  it('falls back to lastVerifiedAt on equal expiry: newer verification is not stale', () => {
    expect(isStaleIosEntitlement(stored, incoming({ expiryTimeMillis: 2000, lastVerifiedAt: 101 }))).toBe(false);
  });

  it('is not stale when the incoming expiry is newer, even if verified earlier', () => {
    expect(isStaleIosEntitlement(stored, incoming({ expiryTimeMillis: 3000, lastVerifiedAt: 1 }))).toBe(false);
  });

  it('does not apply the expiry rule when the stored doc comes from play', () => {
    const playStored = { source: 'play', expiryTimeMillis: 5000, lastVerifiedAt: 100 };
    expect(isStaleIosEntitlement(playStored, incoming({ expiryTimeMillis: 1000, lastVerifiedAt: 200 }))).toBe(false);
  });

  it('does not apply the expiry rule when the stored source is missing', () => {
    const unknownStored = { expiryTimeMillis: 5000, lastVerifiedAt: 100 };
    expect(isStaleIosEntitlement(unknownStored, incoming({ expiryTimeMillis: 1000, lastVerifiedAt: 200 }))).toBe(false);
  });

  it('does not apply the expiry rule when the stored expiry is missing or null', () => {
    expect(
      isStaleIosEntitlement({ source: 'sync-ios', expiryTimeMillis: null, lastVerifiedAt: 100 }, incoming({ lastVerifiedAt: 200 })),
    ).toBe(false);
    expect(isStaleIosEntitlement({ source: 'sync-ios', lastVerifiedAt: 100 }, incoming({ lastVerifiedAt: 200 }))).toBe(false);
  });

  it('does not apply the expiry rule when the incoming expiry is null', () => {
    expect(isStaleIosEntitlement(stored, incoming({ expiryTimeMillis: null, lastVerifiedAt: 200 }))).toBe(false);
  });

  it('is not stale for a revocation of the latest transaction (same expiry, verified later)', () => {
    const revoked = toAppStoreEntitlement(
      {
        transactionId: 'txn-x',
        productId: 'pro_monthly',
        expiresDate: 2000,
        type: 'Auto-Renewable Subscription',
        revocationDate: 1500,
        environment: 'Production',
      },
      300,
    );
    expect(revoked.tier).toBe('free');
    expect(revoked.status).toBe('REVOKED');
    expect(isStaleIosEntitlement(stored, revoked)).toBe(false);
  });

  it('is not stale when nothing is stored yet', () => {
    expect(isStaleIosEntitlement(undefined, incoming())).toBe(false);
  });
});
