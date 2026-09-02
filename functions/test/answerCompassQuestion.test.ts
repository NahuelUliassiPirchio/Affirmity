import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

// Risk WARNING fix: `answerCompassQuestion` only did a bare truthy check on `questionId`, so a
// non-string value (object/array/number) or an implausibly long string would pass through
// unvalidated and get stored verbatim via `.set()`. This file exercises the HTTP handler directly,
// mirroring `sendNotification.test.ts`'s mocking pattern -- no handler-level test existed for this
// endpoint before this batch (only `compassAnswers.test.ts`'s pure-function coverage did).

const boundary = vi.hoisted(() => ({
  db: undefined as unknown,
  verifyIdToken: vi.fn(),
  setWrites: [] as Array<{ path: string; data: unknown }>,
}));

vi.mock('firebase-admin/app', () => ({
  getApps: () => [{}],
  initializeApp: vi.fn(),
}));

vi.mock('firebase-admin/firestore', () => ({
  FieldValue: { serverTimestamp: vi.fn() },
  getFirestore: () => boundary.db,
}));

vi.mock('firebase-admin/messaging', () => ({
  getMessaging: () => ({ send: vi.fn() }),
}));

vi.mock('firebase-admin/auth', () => ({
  getAuth: () => ({ verifyIdToken: boundary.verifyIdToken }),
}));

vi.mock('firebase-functions/v2/https', () => ({
  onRequest: (handler: unknown) => handler,
}));

vi.mock('firebase-functions/v2/scheduler', () => ({
  onSchedule: (_schedule: unknown, handler: unknown) => handler,
}));

vi.mock('@google-cloud/tasks', () => ({
  CloudTasksClient: class {
    createTask = vi.fn();
  },
}));

vi.mock('google-auth-library', () => ({
  OAuth2Client: class {
    verifyIdToken = vi.fn();
  },
}));

vi.mock('googleapis', () => ({
  google: {
    auth: { GoogleAuth: class {} },
    androidpublisher: vi.fn(),
  },
}));

const NOW_MILLIS = Date.UTC(2026, 7, 10, 12);
const UID = 'user-1';

function fakeFirestore(settingsData: Record<string, unknown>) {
  return {
    doc(path: string) {
      return {
        async get() {
          if (path.endsWith('/settings/preferences')) {
            return { exists: true, data: () => settingsData };
          }
          return { exists: false, data: () => undefined };
        },
        async set(data: unknown) {
          boundary.setWrites.push({ path, data });
        },
      };
    },
  };
}

function responseRecorder() {
  const response = { status: vi.fn(), send: vi.fn(), json: vi.fn() };
  response.status.mockReturnValue(response);
  response.send.mockReturnValue(response);
  response.json.mockReturnValue(response);
  return response;
}

let answerCompassQuestion: typeof import('../src/index').answerCompassQuestion;

beforeAll(async () => {
  process.env.SEND_NOTIFICATION_URL = 'https://example.test/send';
  process.env.NOTIFICATION_INVOKER_SA = 'invoker@example.test';
  ({ answerCompassQuestion } = await import('../src/index'));
});

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(NOW_MILLIS);
  boundary.verifyIdToken.mockReset();
  boundary.verifyIdToken.mockResolvedValue({ uid: UID });
  boundary.setWrites.length = 0;
  boundary.db = fakeFirestore({ timeZone: 'UTC' });
});

afterEach(() => {
  vi.useRealTimers();
});

async function invoke(body: unknown) {
  const response = responseRecorder();
  const request = { method: 'POST', headers: { authorization: 'Bearer id-token' }, body };
  await (answerCompassQuestion as unknown as (req: unknown, res: unknown) => Promise<void>)(
    request,
    response,
  );
  return response;
}

describe('answerCompassQuestion', () => {
  it('accepts a well-shaped string questionId and writes the answer doc', async () => {
    const response = await invoke({ questionId: 'q_042' });

    expect(response.status).toHaveBeenCalledWith(200);
    expect(boundary.setWrites).toHaveLength(1);
    expect(boundary.setWrites[0]?.data).toMatchObject({ questionId: 'q_042' });
  });

  it('rejects a missing questionId with 400 and writes nothing', async () => {
    const response = await invoke({});

    expect(response.status).toHaveBeenCalledWith(400);
    expect(boundary.setWrites).toHaveLength(0);
  });

  it('rejects a non-string questionId (object) with 400 and writes nothing', async () => {
    const response = await invoke({ questionId: { nested: 'value' } });

    expect(response.status).toHaveBeenCalledWith(400);
    expect(boundary.setWrites).toHaveLength(0);
  });

  it('rejects a non-string questionId (number) with 400 and writes nothing', async () => {
    const response = await invoke({ questionId: 42 });

    expect(response.status).toHaveBeenCalledWith(400);
    expect(boundary.setWrites).toHaveLength(0);
  });

  it('rejects an oversized questionId (over 100 chars) with 400 and writes nothing', async () => {
    const response = await invoke({ questionId: 'q_'.repeat(60) });

    expect(response.status).toHaveBeenCalledWith(400);
    expect(boundary.setWrites).toHaveLength(0);
  });
});
