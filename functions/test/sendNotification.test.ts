import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import { resetCopyCatalogCacheForTests, type CopyVariant } from '../src/copyCatalog';

const boundary = vi.hoisted(() => ({
  db: undefined as unknown,
  messagingSend: vi.fn(),
  verifyOidc: vi.fn(),
  createTask: vi.fn(),
  deletedPaths: [] as string[],
  readPaths: [] as string[],
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
  /** `notificationCopy` docs `firestoreCatalogSource().loadEnabledVariants()` returns. Defaults to
   * `[]` so every pre-existing test keeps exercising the legacy pass-through path unchanged --
   * only tests that explicitly seed this exercise real server-rendered copy. */
  catalogVariants: CopyVariant[];
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
    catalogVariants: [],
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
        async set(data: unknown) {
          // notificationState/current + notificationDeliveries/{localDay} writes (design §2/§9),
          // only reached after a successful send. Recorded so tests can assert on the real
          // `variantKey` a server-rendered send writes (not just the legacy-focused tests' happy
          // path, which never inspected this before).
          boundary.setWrites.push({ path, data });
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
        // `firestoreCatalogSource` (index.ts) calls `.collection('notificationCopy').where(...).get()`.
        // Returns `state.catalogVariants` (default `[]`, preserving every pre-existing test's
        // legacy-pass-through behavior); tests exercising real server-rendered copy seed it.
        where() {
          return {
            async get() {
              return { docs: state.catalogVariants.map((variant) => queryDoc(variant.key, variant)) };
            },
          };
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
  boundary.setWrites.length = 0;
  // `loadCopyCatalog` memoizes in module scope for 10 minutes (design §1) -- without resetting
  // here, whichever test seeds `catalogVariants` first would leak its catalog into every later
  // test in this file, since they all share the same imported `../src/index` module instance.
  resetCopyCatalogCacheForTests();
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

function makeVariant(overrides: Partial<CopyVariant> = {}): CopyVariant {
  return {
    key: 'variant_a',
    family: 'reminder',
    context: [],
    placeholders: [],
    enabled: true,
    order: 1,
    locales: {
      es: { title: 'Título ES', body: 'Cuerpo ES' },
      en: { title: 'Title EN', body: 'Body EN' },
    },
    ...overrides,
  };
}

describe('sendNotification', () => {
  it('skips when the current channel setting was disabled after planning', async () => {
    const state = baseState({ settings: { ...baseState().settings, reminder_enabled: false } });

    const response = await invoke(task('reminder'), state);

    expect(response.status).toHaveBeenCalledWith(200);
    expect(response.send).toHaveBeenCalledWith('Skipped: channel-disabled');
    expect(boundary.messagingSend).not.toHaveBeenCalled();
  });

  // design D8: streak/healer/meditation_return toggles are new -- an existing user's settings doc
  // (this test's `baseState()`) has never written `streak_enabled`/`healer_enabled`/
  // `meditation_return_enabled`, and the send handler MUST still treat them as enabled.
  it('does not skip a streak alert as channel-disabled when the toggle field is absent (default-on)', async () => {
    const state = baseState({
      completions: [
        { epochDay: LOCAL_DAY - 2, meditationDone: true, affirmationDone: true },
        { epochDay: LOCAL_DAY - 1, meditationDone: true, affirmationDone: true },
      ],
    });
    boundary.messagingSend.mockResolvedValue('message-id');

    const response = await invoke(task('streak'), state);

    expect(response.send).not.toHaveBeenCalledWith('Skipped: channel-disabled');
    expect(boundary.messagingSend).toHaveBeenCalled();
  });

  it('skips a streak alert as channel-disabled when the Streak-Risk toggle is explicitly false', async () => {
    const state = baseState({
      settings: { ...baseState().settings, streak_enabled: false },
      completions: [
        { epochDay: LOCAL_DAY - 2, meditationDone: true, affirmationDone: true },
        { epochDay: LOCAL_DAY - 1, meditationDone: true, affirmationDone: true },
      ],
    });

    const response = await invoke(task('streak'), state);

    expect(response.status).toHaveBeenCalledWith(200);
    expect(response.send).toHaveBeenCalledWith('Skipped: channel-disabled');
    expect(boundary.messagingSend).not.toHaveBeenCalled();
  });

  it('skips a healer alert as channel-disabled when the Streak Healer toggle is explicitly false', async () => {
    const state = baseState({
      settings: { ...baseState().settings, healer_enabled: false },
      completions: [
        { epochDay: LOCAL_DAY - 3, meditationDone: true, affirmationDone: true },
        { epochDay: LOCAL_DAY - 2, meditationDone: true, affirmationDone: true },
      ],
      healerUses: [],
    });

    const response = await invoke(task('healer'), state);

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

    // The legacy `title`/`streakCount` this task carried are forwarded verbatim (no catalog variant
    // matched with an empty test pool, so rendering falls back to the legacy pass-through per design
    // §7); `family`/`destination`/`ctaKey`/`locale` are always added to `data` per design §7's
    // `V2FcmData` contract, regardless of whether copy was rendered or passed through.
    expect(boundary.messagingSend).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          channel: 'streak',
          streakCount: '3',
          title: 'Keep your streak',
          family: 'streak',
          destination: 'streak_action',
          ctaKey: 'cta_streak',
          locale: 'es',
        }),
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

  // CRITICAL (Resilience): a malformed/partially-seeded `notificationCopy` doc must never crash
  // this handler before it can fall back to legacy pass-through -- `renderCopy` iterating a
  // missing `placeholders` array throws a raw TypeError with no guard, and nothing upstream of
  // the try/catch in index.ts previously caught it.
  it('does not crash on a malformed catalog doc and still sends via the legacy pass-through', async () => {
    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const malformedVariant = {
      key: 'malformed_variant',
      family: 'reminder',
      context: [],
      enabled: true,
      order: 1,
      locales: { es: { title: 'x', body: 'y' }, en: { title: 'x', body: 'y' } },
      // `placeholders` intentionally omitted -- simulates a malformed/partially-seeded doc.
    } as unknown as CopyVariant;
    const state = baseState({ catalogVariants: [malformedVariant] });
    boundary.messagingSend.mockResolvedValue('message-id');

    const response = await invoke(
      { ...task('reminder'), title: 'Legacy title', body: 'Legacy body' },
      state,
    );

    expect(boundary.messagingSend).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({ title: 'Legacy title', body: 'Legacy body' }),
      }),
    );
    expect(response.status).toHaveBeenCalledWith(200);
    expect(response.send).toHaveBeenCalledWith('OK');
    expect(consoleErrorSpy).toHaveBeenCalledWith(
      expect.stringContaining('"event":"notification_send_failed"'),
    );
    consoleErrorSpy.mockRestore();
  });

  // CRITICAL (Reliability): the server-rendered copy path (design §1/§7) was never exercised
  // end-to-end before -- `fakeFirestore` always returned an empty catalog, so every existing test
  // only ever covered the legacy pass-through fallback.
  it('renders copy from the catalog and records the real variantKey end-to-end', async () => {
    const variant = makeVariant({ key: 'reminder_variant_a', family: 'reminder' });
    const state = baseState({ catalogVariants: [variant] });
    boundary.messagingSend.mockResolvedValue('message-id');

    const response = await invoke(task('reminder'), state);

    expect(boundary.messagingSend).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          title: 'Título ES',
          body: 'Cuerpo ES',
          variantKey: 'reminder_variant_a',
        }),
      }),
    );
    expect(response.status).toHaveBeenCalledWith(200);

    const deliveryWrite = boundary.setWrites.find(
      (write) => write.path === `users/user-1/notificationDeliveries/${LOCAL_DAY}`,
    );
    const deliveryData = deliveryWrite?.data as {
      families: { reminder: { variantKey: string } };
    };
    expect(deliveryData.families.reminder.variantKey).toBe('reminder_variant_a');
  });

  it('propagates the selected variant key as the reflection channel\'s questionId', async () => {
    const variant = makeVariant({ key: 'reflection_variant_a', family: 'reflection' });
    const state = baseState({ catalogVariants: [variant] });
    boundary.messagingSend.mockResolvedValue('message-id');

    const response = await invoke(task('reflection'), state);

    expect(boundary.messagingSend).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({ questionId: 'reflection_variant_a' }),
      }),
    );
    expect(response.status).toHaveBeenCalledWith(200);
  });

  it('selects the afternoon-tagged mood variant when the local hour is before 18:00', async () => {
    const afternoon = makeVariant({ key: 'mood_afternoon', family: 'mood', context: ['afternoon'] });
    const evening = makeVariant({ key: 'mood_evening', family: 'mood', context: ['evening'] });
    const state = baseState({ catalogVariants: [afternoon, evening] });
    boundary.messagingSend.mockResolvedValue('message-id');

    // NOW_MILLIS is 12:00 UTC / zone UTC -> local minute 720, before MOOD_EVENING_START_MINUTE (18:00).
    const response = await invoke(task('mood'), state);

    expect(boundary.messagingSend).toHaveBeenCalledWith(
      expect.objectContaining({ data: expect.objectContaining({ variantKey: 'mood_afternoon' }) }),
    );
    expect(response.status).toHaveBeenCalledWith(200);
  });

  it('selects the evening-tagged mood variant when the local hour is 18:00 or later', async () => {
    vi.setSystemTime(Date.UTC(2026, 7, 10, 19)); // 19:00 UTC / zone UTC -> local minute 1140, past 18:00.
    const afternoon = makeVariant({ key: 'mood_afternoon', family: 'mood', context: ['afternoon'] });
    const evening = makeVariant({ key: 'mood_evening', family: 'mood', context: ['evening'] });
    const state = baseState({ catalogVariants: [afternoon, evening] });
    boundary.messagingSend.mockResolvedValue('message-id');

    const response = await invoke(task('mood'), state);

    expect(boundary.messagingSend).toHaveBeenCalledWith(
      expect.objectContaining({ data: expect.objectContaining({ variantKey: 'mood_evening' }) }),
    );
    expect(response.status).toHaveBeenCalledWith(200);
  });
});
