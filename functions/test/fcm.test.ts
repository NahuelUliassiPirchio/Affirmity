import { describe, expect, it, vi } from 'vitest';

import { sendAndPrune, type FcmClient, type TokenStore } from '../src/fcm';

function throwingClient(code: string): FcmClient {
  return {
    send: vi.fn(async () => {
      throw Object.assign(new Error(code), { code });
    }),
  };
}

describe('sendAndPrune', () => {
  // Spec: "Stale token is removed on send failure" -- UNREGISTERED.
  it('deletes a token that fails with messaging/registration-token-not-registered', async () => {
    const client = throwingClient('messaging/registration-token-not-registered');
    const deleteToken = vi.fn(async () => undefined);
    const store: TokenStore = { deleteToken };

    await sendAndPrune(client, store, 'uid-1', ['tok-1'], { channel: 'reminder' });

    expect(deleteToken).toHaveBeenCalledWith('uid-1', 'tok-1');
  });

  // Spec: "Stale token is removed on send failure" -- INVALID_ARGUMENT.
  it('deletes a token that fails with messaging/invalid-argument', async () => {
    const client = throwingClient('messaging/invalid-argument');
    const deleteToken = vi.fn(async () => undefined);
    const store: TokenStore = { deleteToken };

    await sendAndPrune(client, store, 'uid-1', ['tok-2'], { channel: 'reminder' });

    expect(deleteToken).toHaveBeenCalledWith('uid-1', 'tok-2');
  });

  it('does not delete a token on a transient error', async () => {
    const client = throwingClient('messaging/internal-error');
    const deleteToken = vi.fn(async () => undefined);
    const store: TokenStore = { deleteToken };

    await sendAndPrune(client, store, 'uid-1', ['tok-3'], { channel: 'reminder' });

    expect(deleteToken).not.toHaveBeenCalled();
  });

  // Spec: "MUST NOT retry sending to that same token".
  it('sends exactly once per token and does not retry', async () => {
    const send = vi.fn(async () => undefined);
    const client: FcmClient = { send };
    const store: TokenStore = { deleteToken: vi.fn(async () => undefined) };

    await sendAndPrune(client, store, 'uid-1', ['tok-4'], { channel: 'reminder' });

    expect(send).toHaveBeenCalledTimes(1);
  });

  it('sends independently to multiple tokens and reports per-token results', async () => {
    const client = throwingClient('messaging/registration-token-not-registered');
    const store: TokenStore = { deleteToken: vi.fn(async () => undefined) };

    const results = await sendAndPrune(client, store, 'uid-1', ['tok-5', 'tok-6'], { channel: 'reminder' });

    expect(results).toEqual([
      { token: 'tok-5', success: false, errorCode: 'messaging/registration-token-not-registered' },
      { token: 'tok-6', success: false, errorCode: 'messaging/registration-token-not-registered' },
    ]);
  });
});
