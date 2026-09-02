/**
 * FCM send + stale-token prune (spec's "Cloud Task Fire Sends One FCM Message" requirement).
 * Sends each token at most once per call -- no in-call retry -- and prunes only tokens that fail
 * with a terminal registration-token error.
 */

export interface FcmMessage {
  channel: string;
  title?: string;
  body?: string;
  data?: Record<string, string>;
  ttl?: string;
}

export interface FcmSendResult {
  token: string;
  success: boolean;
  errorCode?: string;
  failureKind?: FcmFailureKind;
}

const FCM_FAILURE_KIND = {
  TERMINAL_TOKEN: 'terminal-token',
  TRANSIENT: 'transient',
  PERMANENT: 'permanent',
} as const;

export type FcmFailureKind = (typeof FCM_FAILURE_KIND)[keyof typeof FCM_FAILURE_KIND];

/** Port-agnostic FCM client; the real implementation wraps `admin.messaging().send(...)`. */
export interface FcmClient {
  send(token: string, message: FcmMessage): Promise<void>;
}

export interface TokenStore {
  deleteToken(uid: string, token: string): Promise<void>;
}

// Covers both the Admin SDK's messaging error codes and the raw gRPC/HTTP names used by the
// legacy/HTTP v1 API, since the exact shape depends on the send path.
const TERMINAL_TOKEN_ERROR_CODES = new Set([
  'messaging/registration-token-not-registered',
  'messaging/invalid-registration-token',
  'messaging/invalid-argument',
  'UNREGISTERED',
  'INVALID_ARGUMENT',
]);

const TRANSIENT_ERROR_CODES = new Set([
  'messaging/internal-error',
  'messaging/server-unavailable',
  'messaging/quota-exceeded',
  'messaging/message-rate-exceeded',
  'messaging/device-message-rate-exceeded',
  'messaging/topics-message-rate-exceeded',
  'messaging/unknown-error',
  'messaging/timeout',
  'INTERNAL',
  'UNAVAILABLE',
  'RESOURCE_EXHAUSTED',
  'DEADLINE_EXCEEDED',
  'QUOTA_EXCEEDED',
]);

function extractErrorCode(err: unknown): string | undefined {
  if (err && typeof err === 'object' && 'code' in err) {
    return String((err as { code: unknown }).code);
  }
  return undefined;
}

function classifyFailure(errorCode: string | undefined): FcmFailureKind {
  if (!errorCode || TRANSIENT_ERROR_CODES.has(errorCode)) return FCM_FAILURE_KIND.TRANSIENT;
  if (TERMINAL_TOKEN_ERROR_CODES.has(errorCode)) return FCM_FAILURE_KIND.TERMINAL_TOKEN;
  return FCM_FAILURE_KIND.PERMANENT;
}

/** Whether Cloud Tasks must retry this delivery rather than acknowledging it as complete. */
export function hasTransientFcmFailures(results: FcmSendResult[]): boolean {
  return results.some((result) => result.failureKind === FCM_FAILURE_KIND.TRANSIENT);
}

/** Sends `message` to each of `tokens` once; deletes tokens that fail with a stale-token error. */
export async function sendAndPrune(
  client: FcmClient,
  tokenStore: TokenStore,
  uid: string,
  tokens: string[],
  message: FcmMessage,
): Promise<FcmSendResult[]> {
  const results: FcmSendResult[] = [];
  for (const token of tokens) {
    try {
      await client.send(token, message);
      results.push({ token, success: true });
    } catch (err) {
      const errorCode = extractErrorCode(err);
      const failureKind = classifyFailure(errorCode);
      results.push({ token, success: false, errorCode, failureKind });
      if (failureKind === FCM_FAILURE_KIND.TERMINAL_TOKEN) {
        await tokenStore.deleteToken(uid, token);
      }
    }
  }
  return results;
}
