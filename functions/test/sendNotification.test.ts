import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

const boundary = vi.hoisted(() => ({
  db: undefined as unknown,
  messagingSend: vi.fn(),
  verifyOidc: vi.fn(),
  createTask: vi.fn(),
  deletedPaths: [] as string[],
  readPaths: [] as string[],
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
  getMessaging: () => ({ send: boundary.messagingSend }),
}));

vi.mock('firebase-admin/auth', () => ({
  getAuth: () => ({ verifyIdToken: vi.fn() }),
}));

vi.mock('firebase-functions/v2/https', () => ({
  onRequest: (handler: unknown) => handler,
}));

vi.mock('firebase-functions/v2/scheduler', () => ({
  onSchedule: (_schedule: unknown, handler: unknown) => handler,
}));

vi.mock('@google-cloud/tasks', () => ({
  CloudTasksClient: class {
    createTask = boundary.createTask;
  },
}));

vi.mock('google-auth-library', () => ({
  OAuth2Client: class {
    verifyIdToken = boundary.verifyOidc;
  },
}));

vi.mock('googleapis', () => ({
  google: {
    auth: { GoogleAuth: class {} },
    androidpublisher: vi.fn(),
  },
}));

interface CompletionDoc {
  epochDay: number;
  meditationDone: boolean;
  affirmationDone: boolean;
}

interface HandlerState {
  settings: Record<string, unknown>;
  completions: CompletionDoc[];
  healerUses: number[];
  tokens: string[];
  moodExists: boolean;
}

interface TaskBody {
  uid: string;
  channel: string;
  localDay: number;
  title?: string;
  body?: string;
  data?: Record<string, string>;
}

const LOCAL_DAY = Math.floor(Date.UTC(2026, 7, 10) / 86_400_000);
const NOW_MILLIS = Date.UTC(2026, 7, 10, 12);

function baseState(overrides: Partial<HandlerState> = {}): HandlerState {
  return {
    settings: {
      reminder_enabled: true,
      reflection_enabled: true,
      mood_enabled: true,
      quietHours_enabled: false,
      quietHours_startMinute: 23 * 60,
      quietHours_endMinute: 7 * 60,
      timeZone: 'UTC',
    },
    completions: [],
    healerUses: [],
    tokens: ['token-1'],
    moodExists: false,
    ...overrides,
  };
}

function snapshot(exists: boolean, data: Record<string, unknown> = {}) {
  return { exists, data: () => data };
}

function queryDoc(id: string, data: Record<string, unknown> = {}) {
  return { id, data: () => data };
}

function fakeFirestore(state: HandlerState) {
  return {
    doc(path: string) {
      return {
        async get() {
          boundary.readPaths.push(path);
          if (path.endsWith('/settings/preferences')) return snapshot(true, state.settings);
          if (path.includes('/dailyMoods/')) return snapshot(state.moodExists);
          return snapshot(false);
        },
        async delete() {
          boundary.deletedPaths.push(path);
        },
      };
    },
    collection(path: string) {
      return {
        async get() {
          if (path.endsWith('/dailyCompletions')) {
            return {
              docs: state.completions.map((completion) => queryDoc(String(completion.epochDay), completion)),
            };
          }
          if (path.endsWith('/streakHealerUses')) {
            return {
              docs: state.healerUses.map((healedEpochDay) =>
                queryDoc(String(healedEpochDay), { healedEpochDay }),
              ),
            };
          }
          if (path.endsWith('/fcmTokens')) {
            return { docs: state.tokens.map((token) => queryDoc(token)) };
          }
          return { docs: [] };
        },
      };
    },
  };
}

function responseRecorder() {
  const response = {
    status: vi.fn(),
    send: vi.fn(),
  };
  response.status.mockReturnValue(response);
  response.send.mockReturnValue(response);
  return response;
}

let sendNotification: typeof import('../src/index').sendNotification;

beforeAll(async () => {
  process.env.SEND_NOTIFICATION_URL = 'https://example.test/send';
  process.env.NOTIFICATION_INVOKER_SA = 'invoker@example.test';
  ({ sendNotification } = await import('../src/index'));
});

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(NOW_MILLIS);
  boundary.messagingSend.mockReset();
  boundary.verifyOidc.mockReset();
  boundary.verifyOidc.mockResolvedValue({
    getPayload: () => ({ email: 'invoker@example.test' }),
  });
  boundary.deletedPaths.length = 0;
  boundary.readPaths.length = 0;
});

afterEach(() => {
  vi.useRealTimers();
});

async function invoke(body: TaskBody, state: HandlerState = baseState()) {
  boundary.db = fakeFirestore(state);
  const response = responseRecorder();
  const request = { headers: { authorization: 'Bearer task-token' }, body };
  await (sendNotification as unknown as (
    req: unknown,
    res: unknown,
  ) => Promise<void>)(request, response);
  return response;
}

function task(channel: string): TaskBody {
  return { uid: 'user-1', channel, localDay: LOCAL_DAY };
}

describe('sendNotification', () => {
  it('skips when the current channel setting was disabled after planning', async () => {
    const state = baseState({ settings: { ...baseState().settings, reminder_enabled: false } });

    const response = await invoke(task('reminder'), state);

    expect(response.status).toHaveBeenCalledWith(200);
    expect(response.send).toHaveBeenCalledWith('Skipped: channel-disabled');
    expect(boundary.messagingSend).not.toHaveBeenCalled();
  });

  it('skips when the current local time is inside newly-enabled quiet hours', async () => {
    const state = baseState({
      settings: {
        ...baseState().settings,
        quietHours_enabled: true,
        quietHours_startMinute: 11 * 60,
        quietHours_endMinute: 13 * 60,
      },
    });

    const response = await invoke(task('reflection'), state);

    expect(response.status).toHaveBeenCalledWith(200);
    expect(response.send).toHaveBeenCalledWith('Skipped: quiet-hours');
    expect(boundary.messagingSend).not.toHaveBeenCalled();
  });

  it('skips a streak alert after both of today\'s completions were recorded', async () => {
    const state = baseState({
      completions: [
        { epochDay: LOCAL_DAY - 1, meditationDone: true, affirmationDone: true },
        { epochDay: LOCAL_DAY, meditationDone: true, affirmationDone: true },
      ],
    });

    const response = await invoke(task('streak'), state);

    expect(response.status).toHaveBeenCalledWith(200);
    expect(response.send).toHaveBeenCalledWith('Skipped: streak-no-longer-at-risk');
    expect(boundary.messagingSend).not.toHaveBeenCalled();
  });

  it('skips a healer alert after the healer was activated', async () => {
    const state = baseState({
      completions: [
        { epochDay: LOCAL_DAY - 3, meditationDone: true, affirmationDone: true },
        { epochDay: LOCAL_DAY - 2, meditationDone: true, affirmationDone: true },
      ],
      healerUses: [LOCAL_DAY - 1],
    });

    const response = await invoke(task('healer'), state);

    expect(response.status).toHaveBeenCalledWith(200);
    expect(response.send).toHaveBeenCalledWith('Skipped: healer-no-longer-available');
    expect(boundary.messagingSend).not.toHaveBeenCalled();
  });

  it('re-reads today\'s mood document and skips when it already exists', async () => {
    const response = await invoke(task('mood'), baseState({ moodExists: true }));

    expect(boundary.readPaths).toContain(`users/user-1/dailyMoods/${LOCAL_DAY}`);
    expect(response.status).toHaveBeenCalledWith(200);
    expect(response.send).toHaveBeenCalledWith('Skipped: mood-already-logged');
    expect(boundary.messagingSend).not.toHaveBeenCalled();
  });

  it('forwards structured streak data without reintroducing a server-formatted body', async () => {
    const state = baseState({
      completions: [
        { epochDay: LOCAL_DAY - 3, meditationDone: true, affirmationDone: true },
        { epochDay: LOCAL_DAY - 2, meditationDone: true, affirmationDone: true },
        { epochDay: LOCAL_DAY - 1, meditationDone: true, affirmationDone: true },
      ],
    });
    boundary.messagingSend.mockResolvedValue('message-id');

    const response = await invoke(
      {
        ...task('streak'),
        title: 'Keep your streak',
        data: { streakCount: '3' },
      },
      state,
    );

    expect(boundary.messagingSend).toHaveBeenCalledWith(
      expect.objectContaining({
        data: {
          channel: 'streak',
          streakCount: '3',
          title: 'Keep your streak',
        },
      }),
    );
    expect(response.status).toHaveBeenCalledWith(200);
  });

  it('wires the target-day TTL to Firebase Admin in milliseconds', async () => {
    boundary.messagingSend.mockResolvedValue('message-id');

    const response = await invoke(task('reminder'));

    expect(boundary.messagingSend).toHaveBeenCalledWith(
      expect.objectContaining({
        android: expect.objectContaining({ ttl: 14_400_000 }),
      }),
    );
    expect(response.status).toHaveBeenCalledWith(200);
  });

  it('prunes a terminal token failure and acknowledges the task', async () => {
    boundary.messagingSend.mockRejectedValue(
      Object.assign(new Error('invalid token'), { code: 'messaging/invalid-argument' }),
    );

    const response = await invoke(task('reminder'));

    expect(boundary.deletedPaths).toContain('users/user-1/fcmTokens/token-1');
    expect(response.status).toHaveBeenCalledWith(200);
    expect(response.send).toHaveBeenCalledWith('OK');
  });

  it('returns HTTP 503 when any FCM result is transient', async () => {
    boundary.messagingSend.mockRejectedValue(
      Object.assign(new Error('unavailable'), { code: 'messaging/server-unavailable' }),
    );

    const response = await invoke(task('reminder'));

    expect(response.status).toHaveBeenCalledWith(503);
    expect(response.send).toHaveBeenCalledWith('Transient FCM failure');
  });

  it('still requests a task retry after mixed success and transient token results', async () => {
    const state = baseState({ tokens: ['token-success', 'token-transient'] });
    boundary.messagingSend
      .mockResolvedValueOnce('message-id')
      .mockRejectedValueOnce(
        Object.assign(new Error('internal'), { code: 'messaging/internal-error' }),
      );

    const response = await invoke(task('reminder'), state);

    expect(boundary.messagingSend).toHaveBeenCalledTimes(2);
    expect(response.status).toHaveBeenCalledWith(503);
  });
});
