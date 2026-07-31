/**
 * FCM send + stale-token prune (spec's "Cloud Task Fire Sends One FCM Message" requirement).
 * Sends each token at most once per call -- no in-call retry -- and prunes tokens that fail with
 * an unregistered/invalid-argument style error.
 */

export interface FcmMessage {
  channel: string;
  title?: string;
  body?: string;
}

export interface FcmSendResult {
  token: string;
  success: boolean;
  errorCode?: string;
}

/** Port-agnostic FCM client; the real implementation wraps `admin.messaging().send(...)`. */
export interface FcmClient {
  send(token: string, message: FcmMessage): Promise<void>;
}

export interface TokenStore {
  deleteToken(uid: string, token: string): Promise<void>;
}

// Covers both the Admin SDK's messaging error codes and the raw gRPC/HTTP names used by the
// legacy/HTTP v1 API, since the exact shape depends on the send path.
const PRUNE_ERROR_CODES = new Set([
  'messaging/registration-token-not-registered',
  'messaging/invalid-argument',
  'UNREGISTERED',
  'INVALID_ARGUMENT',
]);

function extractErrorCode(err: unknown): string | undefined {
  if (err && typeof err === 'object' && 'code' in err) {
    return String((err as { code: unknown }).code);
  }
  return undefined;
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
      results.push({ token, success: false, errorCode });
      if (errorCode && PRUNE_ERROR_CODES.has(errorCode)) {
        await tokenStore.deleteToken(uid, token);
      }
    }
  }
  return results;
}
