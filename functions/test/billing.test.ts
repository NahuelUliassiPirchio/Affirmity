import { describe, expect, it, vi } from 'vitest';

import {
  classifyNotification,
  handleRtdn,
  isTrustedPushClaims,
  parsePubSubEnvelope,
  resolveEntitlement,
  toEntitlement,
  type EntitlementStore,
  type PlayApiClient,
  type PlaySubscriptionV2,
} from '../src/billing';

const PACKAGE_NAME = 'com.pirxhio.affirmity';
const AUDIENCE = 'https://example.com/playRtdn';
const SERVICE_ACCOUNT = 'rtdn-push@my-project.iam.gserviceaccount.com';

function trustedClaims(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    iss: 'https://accounts.google.com',
    aud: AUDIENCE,
    email: SERVICE_ACCOUNT,
    email_verified: true,
    ...overrides,
  };
}

function envelopeFor(notification: unknown) {
  const data = Buffer.from(JSON.stringify(notification), 'utf8').toString('base64');
  return { message: { data, messageId: 'msg-1' }, subscription: 'projects/p/subscriptions/s' };
}

function subscriptionNotification(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    version: '1.0',
    packageName: PACKAGE_NAME,
    eventTimeMillis: '1700000000000',
    subscriptionNotification: {
      version: '1.0',
      notificationType: 4,
      purchaseToken: 'token-abc',
      subscriptionId: 'pro',
    },
    ...overrides,
  };
}

// Spec: "Verified purchase grants entitlement" / "Unverifiable notification is rejected" --
// claim policy rejects wrong iss/aud/email/email_verified.
describe('isTrustedPushClaims', () => {
  const policy = { audience: AUDIENCE, serviceAccountEmail: SERVICE_ACCOUNT };

  it('accepts claims that match every dimension of the policy', () => {
    expect(isTrustedPushClaims(trustedClaims(), policy)).toBe(true);
  });

  it('rejects an untrusted issuer', () => {
    expect(isTrustedPushClaims(trustedClaims({ iss: 'https://evil.example.com' }), policy)).toBe(false);
  });

  it('rejects a mismatched audience', () => {
    expect(isTrustedPushClaims(trustedClaims({ aud: 'https://wrong-audience.example.com' }), policy)).toBe(false);
  });

  it('rejects an email that is not the configured push service account', () => {
    expect(isTrustedPushClaims(trustedClaims({ email: 'someone-else@example.com' }), policy)).toBe(false);
  });

  it('rejects when email_verified is false', () => {
    expect(isTrustedPushClaims(trustedClaims({ email_verified: false }), policy)).toBe(false);
  });

  it('rejects null claims', () => {
    expect(isTrustedPushClaims(null, policy)).toBe(false);
  });
});

describe('parsePubSubEnvelope + classifyNotification', () => {
  it('drops a malformed envelope (missing message.data) without throwing', () => {
    const parsed = parsePubSubEnvelope({ message: {}, subscription: 'x' });
    expect(parsed).toBeNull();
    expect(classifyNotification(parsed, PACKAGE_NAME)).toEqual({ kind: 'drop', reason: 'malformed' });
  });

  it('drops a notification for a foreign package name', () => {
    const parsed = parsePubSubEnvelope(envelopeFor(subscriptionNotification({ packageName: 'com.other.app' })));
    expect(classifyNotification(parsed, PACKAGE_NAME)).toEqual({ kind: 'drop', reason: 'foreign-package' });
  });

  it('drops a test notification', () => {
    const parsed = parsePubSubEnvelope(
      envelopeFor({ version: '1.0', packageName: PACKAGE_NAME, eventTimeMillis: '1', testNotification: { version: '1.0' } }),
    );
    expect(classifyNotification(parsed, PACKAGE_NAME)).toEqual({ kind: 'drop', reason: 'test-notification' });
  });

  it('classifies a real subscription notification for re-fetch', () => {
    const parsed = parsePubSubEnvelope(envelopeFor(subscriptionNotification()));
    expect(classifyNotification(parsed, PACKAGE_NAME)).toEqual({
      kind: 'subscription',
      purchaseToken: 'token-abc',
      subscriptionId: 'pro',
      notificationType: 4,
    });
  });
});

// Spec: "state→tier mapping incl. grace/hold/expired/voided" (design D1/D4).
describe('toEntitlement', () => {
  const baseSubscription = (state: string): PlaySubscriptionV2 => ({
    subscriptionState: state,
    lineItems: [
      {
        productId: 'pro',
        expiryTime: '2026-09-01T00:00:00.000Z',
        autoRenewingPlan: { autoRenewEnabled: true, basePlanId: 'monthly' },
      },
    ],
    externalAccountIdentifiers: { obfuscatedExternalAccountId: 'uid-1' },
  });

  it('maps ACTIVE to pro', () => {
    expect(toEntitlement(baseSubscription('SUBSCRIPTION_STATE_ACTIVE'), 'token-1', 'rtdn', 1000).tier).toBe('pro');
  });

  it('maps IN_GRACE_PERIOD to pro (still has access)', () => {
    expect(toEntitlement(baseSubscription('SUBSCRIPTION_STATE_IN_GRACE_PERIOD'), 'token-1', 'rtdn', 1000).tier).toBe('pro');
  });

  it('maps ON_HOLD to free (access suspended)', () => {
    expect(toEntitlement(baseSubscription('SUBSCRIPTION_STATE_ON_HOLD'), 'token-1', 'rtdn', 1000).tier).toBe('free');
  });

  it('maps EXPIRED to free', () => {
    expect(toEntitlement(baseSubscription('SUBSCRIPTION_STATE_EXPIRED'), 'token-1', 'rtdn', 1000).tier).toBe('free');
  });

  it('maps REVOKED (voided) to free', () => {
    expect(toEntitlement(baseSubscription('SUBSCRIPTION_STATE_REVOKED'), 'token-1', 'rtdn', 1000).tier).toBe('free');
  });

  it('stores a hash of the purchase token, never the raw token', () => {
    const doc = toEntitlement(baseSubscription('SUBSCRIPTION_STATE_ACTIVE'), 'super-secret-token', 'rtdn', 1000);
    expect(doc.purchaseTokenHash).not.toContain('super-secret-token');
    expect(doc.purchaseTokenHash).toMatch(/^[a-f0-9]{64}$/);
  });

  it('carries productId, expiryTimeMillis and autoRenewing from the line item', () => {
    const doc = toEntitlement(baseSubscription('SUBSCRIPTION_STATE_ACTIVE'), 'token-1', 'rtdn', 1000);
    expect(doc.productId).toBe('pro');
    expect(doc.basePlanId).toBe('monthly');
    expect(doc.expiryTimeMillis).toBe(Date.parse('2026-09-01T00:00:00.000Z'));
    expect(doc.autoRenewing).toBe(true);
  });
});

describe('resolveEntitlement', () => {
  function storeWithLastVerifiedAt(lastVerifiedAt: number | null): EntitlementStore {
    return {
      getLastVerifiedAt: vi.fn(async () => lastVerifiedAt),
      writeEntitlement: vi.fn(async () => undefined),
    };
  }

  function playApiReturning(subscription: PlaySubscriptionV2): PlayApiClient {
    return { getSubscription: vi.fn(async () => subscription) };
  }

  const activeSubscription: PlaySubscriptionV2 = {
    subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE',
    lineItems: [{ productId: 'pro', expiryTime: '2026-09-01T00:00:00.000Z', autoRenewingPlan: { autoRenewEnabled: true } }],
    externalAccountIdentifiers: { obfuscatedExternalAccountId: 'uid-1' },
  };

  it('writes the resolved entitlement when this verification is newer than the stored one', async () => {
    const store = storeWithLastVerifiedAt(1000);
    const playApi = playApiReturning(activeSubscription);

    const result = await resolveEntitlement(playApi, store, PACKAGE_NAME, 'token-1', 'rtdn', 2000);

    expect(result.outcome).toBe('written');
    expect(store.writeEntitlement).toHaveBeenCalledWith('uid-1', expect.objectContaining({ tier: 'pro' }));
  });

  // Spec: idempotency -- a stale redelivery whose lastVerifiedAt is older than the stored one is dropped.
  it('drops a stale redelivery without writing', async () => {
    const store = storeWithLastVerifiedAt(5000);
    const playApi = playApiReturning(activeSubscription);

    const result = await resolveEntitlement(playApi, store, PACKAGE_NAME, 'token-1', 'rtdn', 2000);

    expect(result.outcome).toBe('dropped-stale');
    expect(store.writeEntitlement).not.toHaveBeenCalled();
  });

  it('reports no-uid when the subscription has no obfuscated account id', async () => {
    const store = storeWithLastVerifiedAt(null);
    const playApi = playApiReturning({ ...activeSubscription, externalAccountIdentifiers: undefined });

    const result = await resolveEntitlement(playApi, store, PACKAGE_NAME, 'token-1', 'rtdn', 2000);

    expect(result.outcome).toBe('no-uid');
    expect(store.writeEntitlement).not.toHaveBeenCalled();
  });
});

describe('handleRtdn', () => {
  const policy = { audience: AUDIENCE, serviceAccountEmail: SERVICE_ACCOUNT };

  function baseParams(overrides: Partial<Record<string, unknown>> = {}) {
    return {
      claims: trustedClaims(),
      claimPolicy: policy,
      body: envelopeFor(subscriptionNotification()),
      packageName: PACKAGE_NAME,
      playApi: {
        getSubscription: vi.fn(async () => ({
          subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE',
          lineItems: [{ productId: 'pro', expiryTime: '2026-09-01T00:00:00.000Z' }],
          externalAccountIdentifiers: { obfuscatedExternalAccountId: 'uid-1' },
        })),
      },
      store: {
        getLastVerifiedAt: vi.fn(async () => null),
        writeEntitlement: vi.fn(async () => undefined),
      },
      nowMillis: 2000,
      ...overrides,
    };
  }

  it('rejects an untrusted caller with 401 and never touches the Play API', async () => {
    const params = baseParams({ claims: trustedClaims({ email_verified: false }) });

    const result = await handleRtdn(params as never);

    expect(result.status).toBe(401);
    expect((params.playApi as PlayApiClient).getSubscription).not.toHaveBeenCalled();
  });

  it('drops a malformed body as 200 without calling the Play API', async () => {
    const params = baseParams({ body: { message: {}, subscription: 'x' } });

    const result = await handleRtdn(params as never);

    expect(result).toMatchObject({ status: 200, reason: 'malformed' });
    expect((params.playApi as PlayApiClient).getSubscription).not.toHaveBeenCalled();
  });

  it('writes entitlement and returns 200 for a verified, trusted subscription notification', async () => {
    const params = baseParams();

    const result = await handleRtdn(params as never);

    expect(result.status).toBe(200);
    expect((params.store as EntitlementStore).writeEntitlement).toHaveBeenCalledWith(
      'uid-1',
      expect.objectContaining({ tier: 'pro' }),
    );
  });

  // Spec: transient Play-API/Firestore failure -> 500 so Pub/Sub backs off and redelivers.
  it('returns 500 on a transient Play API failure so Pub/Sub retries', async () => {
    const params = baseParams({
      playApi: { getSubscription: vi.fn(async () => { throw new Error('ECONNRESET'); }) },
    });

    const result = await handleRtdn(params as never);

    expect(result.status).toBe(500);
  });
});
