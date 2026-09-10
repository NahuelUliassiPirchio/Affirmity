/**
 * Play Billing entitlement resolution: RTDN push-claim trust policy, Pub/Sub envelope parsing,
 * notification classification, Play Developer API subscription -> entitlement-doc mapping, and
 * the idempotent write orchestration shared by both server entry points (`playRtdn`, RTDN-driven,
 * and `syncEntitlement`, client-triggered) -- design.md's D1/D2. Pure logic only; SDK wiring
 * (OIDC verification, Firestore, the real Play Developer API client) lives in `index.ts`, matching
 * the split already used by `fcm.ts`/`tasks.ts`.
 */

import { createHash } from 'node:crypto';

// ---------------------------------------------------------------------------------------------
// D1: RTDN push-claim trust policy (transport-auth leg). The RTDN body itself is never trusted --
// it is only a hint to re-fetch from the Play Developer API (see resolveEntitlement below).
// ---------------------------------------------------------------------------------------------

export interface PushClaimPolicy {
  audience: string;
  serviceAccountEmail: string;
}

export interface OidcClaims {
  iss?: string;
  aud?: string;
  email?: string;
  email_verified?: boolean;
}

const TRUSTED_ISSUERS = new Set(['https://accounts.google.com', 'accounts.google.com']);

/** Claim policy check for a verified Pub/Sub push OIDC token (design D1, step 3). */
export function isTrustedPushClaims(
  claims: OidcClaims | null | undefined,
  policy: PushClaimPolicy,
): boolean {
  if (!claims) return false;
  if (!claims.iss || !TRUSTED_ISSUERS.has(claims.iss)) return false;
  if (claims.email !== policy.serviceAccountEmail) return false;
  if (claims.email_verified !== true) return false;
  if (claims.aud !== policy.audience) return false;
  return true;
}

// ---------------------------------------------------------------------------------------------
// Pub/Sub envelope parsing + notification classification.
// ---------------------------------------------------------------------------------------------

export interface PubSubEnvelope {
  message?: { data?: string; messageId?: string; publishTime?: string };
  subscription?: string;
}

export interface DeveloperNotification {
  version: string;
  packageName: string;
  eventTimeMillis: string;
  subscriptionNotification?: {
    version: string;
    notificationType: number;
    purchaseToken: string;
    subscriptionId: string;
  };
  oneTimeProductNotification?: {
    version: string;
    notificationType: number;
    purchaseToken: string;
    sku: string;
  };
  testNotification?: { version: string };
}

/** Decodes the base64 Pub/Sub `message.data` payload into a [DeveloperNotification]. Returns
 * `null` (never throws) on any malformed shape, so callers can drop-not-retry (design D1). */
export function parsePubSubEnvelope(body: unknown): DeveloperNotification | null {
  if (!body || typeof body !== 'object') return null;
  const envelope = body as PubSubEnvelope;
  const data = envelope.message?.data;
  if (!data || typeof data !== 'string') return null;
  try {
    const decoded = Buffer.from(data, 'base64').toString('utf8');
    const parsed: unknown = JSON.parse(decoded);
    if (!parsed || typeof parsed !== 'object') return null;
    if (typeof (parsed as { packageName?: unknown }).packageName !== 'string') return null;
    return parsed as DeveloperNotification;
  } catch {
    return null;
  }
}

export type NotificationClassification =
  | { kind: 'subscription'; purchaseToken: string; subscriptionId: string; notificationType: number }
  | { kind: 'drop'; reason: string };

/** Sorts a parsed notification into "worth re-fetching from Play" vs. "drop, do not retry"
 * (design D1's status contract). Foreign package, test pings, and one-time-product notifications
 * (out of scope -- subscriptions only) are all dropped without touching the Play API. */
export function classifyNotification(
  notification: DeveloperNotification | null,
  expectedPackageName: string,
): NotificationClassification {
  if (!notification) return { kind: 'drop', reason: 'malformed' };
  if (notification.packageName !== expectedPackageName) return { kind: 'drop', reason: 'foreign-package' };
  if (notification.testNotification) return { kind: 'drop', reason: 'test-notification' };
  if (notification.oneTimeProductNotification) return { kind: 'drop', reason: 'one-time-product-not-supported' };
  const sub = notification.subscriptionNotification;
  if (!sub || !sub.purchaseToken || !sub.subscriptionId) {
    return { kind: 'drop', reason: 'unknown-notification-type' };
  }
  return {
    kind: 'subscription',
    purchaseToken: sub.purchaseToken,
    subscriptionId: sub.subscriptionId,
    notificationType: sub.notificationType,
  };
}

// ---------------------------------------------------------------------------------------------
// Play Developer API -> entitlement-doc mapping (design D4's doc shape; state->tier per D1).
// ---------------------------------------------------------------------------------------------

export type EntitlementTier = 'free' | 'pro';

/** States that still grant Pro access. ACTIVE and IN_GRACE_PERIOD obviously do; CANCELED means
 * the user turned off auto-renew but Play still honors access until `expiryTime` -- the client's
 * `resolveTier(doc, nowMillis)` (design D5) is what actually flips it to Free once that passes. */
const PRO_STATES = new Set([
  'SUBSCRIPTION_STATE_ACTIVE',
  'SUBSCRIPTION_STATE_IN_GRACE_PERIOD',
  'SUBSCRIPTION_STATE_CANCELED',
]);

export interface PlaySubscriptionLineItem {
  productId?: string;
  expiryTime?: string;
  autoRenewingPlan?: { autoRenewEnabled?: boolean; basePlanId?: string };
  offerDetails?: { basePlanId?: string };
}

/** Shape of the Play Developer API's `purchases.subscriptionsv2.get` response, trimmed to the
 * fields this function reads. */
export interface PlaySubscriptionV2 {
  subscriptionState: string;
  lineItems?: PlaySubscriptionLineItem[];
  externalAccountIdentifiers?: { obfuscatedExternalAccountId?: string };
}

export interface EntitlementDoc {
  tier: EntitlementTier;
  status: string;
  productId: string | null;
  basePlanId: string | null;
  expiryTimeMillis: number | null;
  autoRenewing: boolean;
  purchaseTokenHash: string;
  lastVerifiedAt: number;
  // 'sync-ios' is written by `appStoreBilling.ts`'s `resolveIosEntitlement` (App Store Server
  // Library JWS verification), not by anything in this file -- included here only so the shared
  // `EntitlementStore.writeEntitlement` port (and the `users/{uid}/entitlements/current` doc
  // shape it writes) stays a single source of truth for both platforms.
  source: 'rtdn' | 'sync' | 'sync-ios';
}

function sha256Hex(input: string): string {
  return createHash('sha256').update(input).digest('hex');
}

/** Maps the Play API's authoritative subscription resource to this app's entitlement doc.
 * Stores only a hash of the purchase token (D4: it is a bearer credential for the Play API and
 * this doc is owner-readable), never the raw token. */
export function toEntitlement(
  subscription: PlaySubscriptionV2,
  purchaseToken: string,
  source: 'rtdn' | 'sync',
  nowMillis: number,
): EntitlementDoc {
  const lineItem = subscription.lineItems?.[0];
  const tier: EntitlementTier = PRO_STATES.has(subscription.subscriptionState) ? 'pro' : 'free';
  const expiryTimeMillis = lineItem?.expiryTime ? Date.parse(lineItem.expiryTime) : null;
  return {
    tier,
    status: subscription.subscriptionState,
    productId: lineItem?.productId ?? null,
    basePlanId: lineItem?.autoRenewingPlan?.basePlanId ?? lineItem?.offerDetails?.basePlanId ?? null,
    expiryTimeMillis,
    autoRenewing: lineItem?.autoRenewingPlan?.autoRenewEnabled ?? false,
    purchaseTokenHash: sha256Hex(purchaseToken),
    lastVerifiedAt: nowMillis,
    source,
  };
}

// ---------------------------------------------------------------------------------------------
// Ports + orchestration shared by playRtdn and syncEntitlement (design D2).
// ---------------------------------------------------------------------------------------------

/** Port-agnostic Play Developer API client; the real implementation wraps `googleapis`'
 * `androidpublisher.purchases.subscriptionsv2.get`. */
export interface PlayApiClient {
  getSubscription(packageName: string, purchaseToken: string): Promise<PlaySubscriptionV2>;
}

/** Port-agnostic entitlement store; the real implementation reads/writes
 * `users/{uid}/entitlements/current` via the Admin SDK. */
export interface EntitlementStore {
  getLastVerifiedAt(uid: string): Promise<number | null>;
  writeEntitlement(uid: string, doc: EntitlementDoc): Promise<void>;
}

export interface ResolveEntitlementResult {
  outcome: 'written' | 'dropped-stale' | 'no-uid';
  uid?: string;
  doc?: EntitlementDoc;
}

/**
 * Re-fetches the subscription from the Play Developer API (the sole source of authority, per
 * design D1) and writes the resulting entitlement, unless a redelivery is older than what is
 * already stored (idempotency: last-write-wins keyed by `lastVerifiedAt`).
 */
export async function resolveEntitlement(
  playApi: PlayApiClient,
  store: EntitlementStore,
  packageName: string,
  purchaseToken: string,
  source: 'rtdn' | 'sync',
  nowMillis: number,
): Promise<ResolveEntitlementResult> {
  const subscription = await playApi.getSubscription(packageName, purchaseToken);
  const uid = subscription.externalAccountIdentifiers?.obfuscatedExternalAccountId;
  if (!uid) return { outcome: 'no-uid' };

  const doc = toEntitlement(subscription, purchaseToken, source, nowMillis);
  const lastVerifiedAt = await store.getLastVerifiedAt(uid);
  if (lastVerifiedAt !== null && doc.lastVerifiedAt <= lastVerifiedAt) {
    return { outcome: 'dropped-stale', uid, doc };
  }

  await store.writeEntitlement(uid, doc);
  return { outcome: 'written', uid, doc };
}

export type RtdnHandlerResult =
  | { status: 401 }
  | { status: 200; reason: string }
  | { status: 500; reason: string }
  | { status: 200; outcome: ResolveEntitlementResult };

export interface HandleRtdnParams {
  claims: OidcClaims | null;
  claimPolicy: PushClaimPolicy;
  body: unknown;
  packageName: string;
  playApi: PlayApiClient;
  store: EntitlementStore;
  nowMillis: number;
}

/**
 * Full `playRtdn` request handling, minus the actual HTTP/Express glue (kept in `index.ts`).
 * Status contract (design D1): auth failure -> 401; malformed/foreign/test notification -> 200
 * (dropped, never retried); transient Play-API/Firestore failure -> 500 (Pub/Sub backs off and
 * redelivers); verified write -> 200.
 */
export async function handleRtdn(params: HandleRtdnParams): Promise<RtdnHandlerResult> {
  if (!isTrustedPushClaims(params.claims, params.claimPolicy)) {
    return { status: 401 };
  }

  const notification = parsePubSubEnvelope(params.body);
  const classification = classifyNotification(notification, params.packageName);
  if (classification.kind === 'drop') {
    return { status: 200, reason: classification.reason };
  }

  try {
    const outcome = await resolveEntitlement(
      params.playApi,
      params.store,
      params.packageName,
      classification.purchaseToken,
      'rtdn',
      params.nowMillis,
    );
    return { status: 200, outcome };
  } catch (err) {
    return { status: 500, reason: err instanceof Error ? err.message : 'unknown-error' };
  }
}
