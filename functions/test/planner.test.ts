import { describe, expect, it, vi } from 'vitest';

import { localInstantMillis } from '../src/localDay';
import {
  applyPriority,
  planAllUsers,
  planAndEnqueueUser,
  type PlannedTask,
  type PlanResult,
  type PlanStore,
  type TaskEnqueuer,
  type UserPlanInput,
} from '../src/planner';

function makeStore(): PlanStore & { plans: Map<string, PlanResult> } {
  const plans = new Map<string, PlanResult>();
  return {
    plans,
    hasPlan: vi.fn(async (uid: string, localDay: number) => plans.has(`${uid}-${localDay}`)),
    markPlanned: vi.fn(async (uid: string, localDay: number, result: PlanResult) => {
      plans.set(`${uid}-${localDay}`, result);
    }),
    markFailed: vi.fn(async () => undefined),
  };
}

function makeEnqueuer(): TaskEnqueuer & { calls: unknown[] } {
  const calls: unknown[] = [];
  return {
    calls,
    enqueue: vi.fn(async (task) => {
      calls.push(task);
      return { created: true };
    }),
  };
}

const baseInput: UserPlanInput = {
  uid: 'user-1',
  localDay: 19000,
  settings: {
    remindersEnabled: true,
    reflectionEnabled: false,
    moodEnabled: false,
    reminderSegments: ['manana', 'tarde'],
    reflectionSegments: [],
    moodSegments: [],
    streakEnabled: true,
    healerEnabled: true,
    meditationReturnEnabled: true,
    quietHoursEnabled: false,
    quietHoursStartMinute: 0,
    quietHoursEndMinute: 0,
    timeZone: 'UTC',
  },
  completions: [],
  healerUses: [],
  meditationReturnState: { absenceStartLocalDay: null, lastSentLocalDay: null, lastBand: null },
};

function planGeneratedAt(input: UserPlanInput): number {
  return input.localDay * 86_400_000 + 3 * 60 * 60_000;
}

function planUser(input: UserPlanInput, store: PlanStore, enqueuer: TaskEnqueuer) {
  return planAndEnqueueUser(input, store, enqueuer, Math.random, planGeneratedAt(input));
}

function planUsers(inputs: UserPlanInput[], store: PlanStore, enqueuer: TaskEnqueuer) {
  return planAllUsers(inputs, store, enqueuer, Math.random, planGeneratedAt(inputs[0]));
}

describe('planAndEnqueueUser', () => {
  it('enqueues 3 reminder tasks and marks the plan as planned', async () => {
    const store = makeStore();
    const enqueuer = makeEnqueuer();

    const result = await planUser(baseInput, store, enqueuer);

    expect(result.status).toBe('planned');
    expect(enqueuer.calls).toHaveLength(3);
    expect(store.plans.has('user-1-19000')).toBe(true);
  });

  it('enqueues nothing when quiet hours cover the whole selected segment span', async () => {
    const store = makeStore();
    const enqueuer = makeEnqueuer();

    const input: UserPlanInput = {
      ...baseInput,
      settings: {
        ...baseInput.settings,
        quietHoursEnabled: true,
        quietHoursStartMinute: 5 * 60,
        quietHoursEndMinute: 19 * 60,
      },
    };

    const result = await planUser(input, store, enqueuer);

    expect(result.status).toBe('planned');
    expect(enqueuer.calls).toHaveLength(0);
  });

  // Design.md: "Planner idempotency ... a second run for the same localDay enqueues nothing".
  it('a second run for the same localDay enqueues nothing (idempotent)', async () => {
    const store = makeStore();
    const enqueuer = makeEnqueuer();

    await planUser(baseInput, store, enqueuer);
    const second = await planUser(baseInput, store, enqueuer);

    expect(second.status).toBe('skipped');
    expect(enqueuer.calls).toHaveLength(3); // unchanged from the first run
  });
});

describe('planAllUsers', () => {
  // Spec: "One user's planning failure does not block others".
  it("isolates one user's planning failure and keeps planning the rest", async () => {
    const store = makeStore();
    const failingEnqueuer: TaskEnqueuer = {
      enqueue: vi.fn(async (task) => {
        if (task.uid === 'bad-user') throw new Error('boom');
        return { created: true };
      }),
    };

    const inputs: UserPlanInput[] = [
      { ...baseInput, uid: 'bad-user' },
      { ...baseInput, uid: 'good-user' },
    ];

    const results = await planUsers(inputs, store, failingEnqueuer);

    expect(results.find((r) => r.uid === 'bad-user')?.status).toBe('failed');
    expect(results.find((r) => r.uid === 'good-user')?.status).toBe('planned');
    expect(store.markFailed).toHaveBeenCalledWith('bad-user', 19000, expect.any(String));
  });

  it('includes the at-risk streak count as structured data without a server-localized body', async () => {
    const store = makeStore();
    const enqueuer = makeEnqueuer();
    const localDay = baseInput.localDay;

    const input: UserPlanInput = {
      ...baseInput,
      completions: [
        { epochDay: localDay - 2, meditationDone: true, affirmationDone: true },
        { epochDay: localDay - 1, meditationDone: true, affirmationDone: true },
      ],
    };

    await planUser(input, store, enqueuer);

    const streakTask = enqueuer.calls.find((task) => (task as { channel: string }).channel === 'streak') as
      | { body?: string; data?: { streakCount?: string } }
      | undefined;
    expect(streakTask?.data?.streakCount).toBe('2');
    expect(streakTask?.body).toBeUndefined();
  });

  it('includes the per-activity streaks and no activity when only one activity holds the streak', async () => {
    const store = makeStore();
    const enqueuer = makeEnqueuer();
    const localDay = baseInput.localDay;

    const input: UserPlanInput = {
      ...baseInput,
      completions: [3, 2, 1].map((back) => ({
        epochDay: localDay - back,
        meditationDone: true,
        affirmationDone: false,
      })),
    };

    await planUser(input, store, enqueuer);

    const streakTask = enqueuer.calls.find((task) => (task as { channel: string }).channel === 'streak') as
      | { data?: Record<string, string> }
      | undefined;
    expect(streakTask?.data).toEqual({ streakCount: '3', meditationStreak: '3', affirmationsStreak: '0' });
  });

  it('includes both per-activity streaks for a mixed general streak', async () => {
    const store = makeStore();
    const enqueuer = makeEnqueuer();
    const localDay = baseInput.localDay;

    await planUser(
      {
        ...baseInput,
        completions: [
          { epochDay: localDay - 2, meditationDone: true, affirmationDone: true },
          { epochDay: localDay - 1, meditationDone: true, affirmationDone: true },
        ],
      },
      store,
      enqueuer,
    );

    const streakTask = enqueuer.calls.find((task) => (task as { channel: string }).channel === 'streak') as
      | { data?: Record<string, string> }
      | undefined;
    expect(streakTask?.data).toEqual({ streakCount: '2', meditationStreak: '2', affirmationsStreak: '2' });
  });

  describe('streak task selection edge cases', () => {
    const streakTaskOf = (enqueuer: ReturnType<typeof makeEnqueuer>) =>
      enqueuer.calls.find((task) => (task as { channel: string }).channel === 'streak') as
        | { atMillis: number; data?: Record<string, string> }
        | undefined;
    const row = (epochDay: number, meditationDone: boolean, affirmationDone: boolean) => ({
      epochDay,
      meditationDone,
      affirmationDone,
    });

    it('enqueues no streak task when the streak ended two days ago', async () => {
      const enqueuer = makeEnqueuer();
      const day = baseInput.localDay;

      await planUser({ ...baseInput, completions: [row(day - 3, true, true), row(day - 2, true, true)] }, makeStore(), enqueuer);

      expect(streakTaskOf(enqueuer)).toBeUndefined();
    });

    it('enqueues one general task with both per-activity streaks when both are live', async () => {
      const enqueuer = makeEnqueuer();
      const day = baseInput.localDay;

      await planUser(
        { ...baseInput, completions: [row(day - 3, true, true), row(day - 2, true, true), row(day - 1, true, true)] },
        makeStore(),
        enqueuer,
      );

      expect(streakTaskOf(enqueuer)?.data).toEqual({ streakCount: '3', meditationStreak: '3', affirmationsStreak: '3' });
    });

    it('enqueues no streak task when an activity is already done today', async () => {
      const enqueuer = makeEnqueuer();
      const day = baseInput.localDay;

      await planUser(
        { ...baseInput, completions: [row(day - 2, true, false), row(day - 1, true, false), row(day, false, true)] },
        makeStore(),
        enqueuer,
      );

      expect(streakTaskOf(enqueuer)).toBeUndefined();
    });

    it('anchors the task at 20:00 of the user-local day in a non-UTC zone', async () => {
      const enqueuer = makeEnqueuer();
      const day = baseInput.localDay;
      const zone = 'Asia/Tokyo';

      await planUser(
        {
          ...baseInput,
          settings: { ...baseInput.settings, timeZone: zone, remindersEnabled: false },
          completions: [row(day - 1, true, true)],
        },
        makeStore(),
        enqueuer,
      );

      expect(streakTaskOf(enqueuer)?.atMillis).toBe(localInstantMillis(day, zone, 20 * 60));
    });
  });

  it('does not enqueue a fixed-time alert once its safe scheduling window has passed', async () => {
    const store = makeStore();
    const enqueuer = makeEnqueuer();
    const localDay = baseInput.localDay;
    const input: UserPlanInput = {
      ...baseInput,
      settings: { ...baseInput.settings, remindersEnabled: false },
      completions: [{ epochDay: localDay - 1, meditationDone: true, affirmationDone: true }],
    };
    const alertTime = localDay * 86_400_000 + 20 * 60 * 60_000;

    await planAndEnqueueUser(input, store, enqueuer, Math.random, alertTime);

    expect(enqueuer.calls).toEqual([]);
  });

  it('fires the healer channel the day after a break when a healer is held', async () => {
    const store = makeStore();
    const enqueuer = makeEnqueuer();
    // 2026-08-10 UTC-epoch-day, well after the healer's 2026-08-04 rollout floor.
    const localDay = 20675;

    const input: UserPlanInput = {
      ...baseInput,
      localDay,
      completions: [
        { epochDay: localDay - 3, meditationDone: true, affirmationDone: true },
        { epochDay: localDay - 2, meditationDone: true, affirmationDone: true },
        // localDay - 1 has no row at all: zero activity, the break day.
      ],
      healerUses: [],
    };

    await planUser(input, store, enqueuer);

    const healerTask = enqueuer.calls.find((task) => (task as { channel: string }).channel === 'healer');
    expect(healerTask).toBeDefined();
  });

  it('plans another missed-day alert from remaining inventory after one activation', async () => {
    const store = makeStore();
    const enqueuer = makeEnqueuer();
    const localDay = 20677;
    await planUser({
      ...baseInput,
      localDay,
      completions: [6, 5, 4, 3].map((offset) => ({
        epochDay: localDay - offset, meditationDone: true, affirmationDone: true,
      })),
      healerUses: [{ healedEpochDay: localDay - 2 }],
    }, store, enqueuer);
    expect(enqueuer.calls.filter((task) => task.channel === 'healer')).toHaveLength(1);
  });

  it('does not fire the healer channel when the break day was already healed', async () => {
    const store = makeStore();
    const enqueuer = makeEnqueuer();
    const localDay = 20675;

    const input: UserPlanInput = {
      ...baseInput,
      localDay,
      completions: [
        { epochDay: localDay - 3, meditationDone: true, affirmationDone: true },
        { epochDay: localDay - 2, meditationDone: true, affirmationDone: true },
      ],
      healerUses: [{ healedEpochDay: localDay - 1 }],
    };

    await planUser(input, store, enqueuer);

    const healerTask = enqueuer.calls.find((task) => (task as { channel: string }).channel === 'healer');
    expect(healerTask).toBeUndefined();
  });

  // spec: "Settings Toggles for Streak-Risk, Healer, and Meditation Return" -- disabling the
  // toggle MUST suppress the family at plan time, even though the underlying trigger condition
  // (currentStreak >= 1, day incomplete) still holds.
  it('does not enqueue a streak alert when the Streak-Risk toggle is disabled', async () => {
    const store = makeStore();
    const enqueuer = makeEnqueuer();
    const localDay = baseInput.localDay;

    const input: UserPlanInput = {
      ...baseInput,
      settings: { ...baseInput.settings, streakEnabled: false },
      completions: [
        { epochDay: localDay - 2, meditationDone: true, affirmationDone: true },
        { epochDay: localDay - 1, meditationDone: true, affirmationDone: true },
      ],
    };

    await planUser(input, store, enqueuer);

    const streakTask = enqueuer.calls.find((task) => (task as { channel: string }).channel === 'streak');
    expect(streakTask).toBeUndefined();
  });

  it('does not enqueue a healer alert when the Streak Healer toggle is disabled', async () => {
    const store = makeStore();
    const enqueuer = makeEnqueuer();
    const localDay = 20675;

    const input: UserPlanInput = {
      ...baseInput,
      localDay,
      settings: { ...baseInput.settings, healerEnabled: false },
      completions: [
        { epochDay: localDay - 3, meditationDone: true, affirmationDone: true },
        { epochDay: localDay - 2, meditationDone: true, affirmationDone: true },
      ],
      healerUses: [],
    };

    await planUser(input, store, enqueuer);

    const healerTask = enqueuer.calls.find((task) => (task as { channel: string }).channel === 'healer');
    expect(healerTask).toBeUndefined();
  });

  it('enqueues surviving reflection slots exactly two hours after mood', async () => {
    const enqueuer = makeEnqueuer();
    const input: UserPlanInput = {
      ...baseInput,
      settings: {
        ...baseInput.settings,
        remindersEnabled: false,
        moodEnabled: true,
        moodSegments: ['manana'],
        reflectionEnabled: true,
        reflectionSegments: ['manana'],
      },
    };

    const randomValues = [1 / 24, 1 / 24, 1 / 24, 0];
    await planAndEnqueueUser(input, makeStore(), enqueuer, () => randomValues.shift() ?? 0, planGeneratedAt(input));

    const dayStart = input.localDay * 86_400_000;
    expect(enqueuer.calls).toEqual([
      ...[0, 1, 2].map((slot) => ({
        uid: input.uid, localDay: input.localDay, channel: 'reflection', slot,
        atMillis: dayStart + 8 * 60 * 60_000,
      })),
      { uid: input.uid, localDay: input.localDay, channel: 'mood', slot: 0, atMillis: dayStart + 6 * 60 * 60_000 },
    ]);
  });

  it('suppresses reflection slots when mood leaves no two-hour gap in their segment', async () => {
    const enqueuer = makeEnqueuer();
    const input: UserPlanInput = {
      ...baseInput,
      settings: {
        ...baseInput.settings,
        remindersEnabled: false,
        moodEnabled: true,
        moodSegments: ['manana'],
        reflectionEnabled: true,
        reflectionSegments: ['manana'],
      },
    };

    const randomValues = [7 / 8, 7 / 8, 7 / 8, 5 / 6];
    await planAndEnqueueUser(input, makeStore(), enqueuer, () => randomValues.shift() ?? 0, planGeneratedAt(input));

    expect(enqueuer.calls).toEqual([
      { uid: input.uid, localDay: input.localDay, channel: 'mood', slot: 0,
        atMillis: input.localDay * 86_400_000 + 11 * 60 * 60_000 },
    ]);
  });

  it('plans nothing for a user missing a timezone', async () => {
    const store = makeStore();
    const enqueuer = makeEnqueuer();

    const results = await planUsers(
      [{ ...baseInput, settings: { ...baseInput.settings, timeZone: null } }],
      store,
      enqueuer,
    );

    expect(results[0].status).toBe('planned');
    expect(results[0].tasks).toHaveLength(0);
    expect(enqueuer.calls).toHaveLength(0);
  });
});

describe('applyPriority', () => {
  const ZONE = 'UTC';
  const LOCAL_DAY = 19000;

  function task(overrides: Partial<PlannedTask>): PlannedTask {
    return {
      uid: 'user-1',
      localDay: LOCAL_DAY,
      channel: 'reminder',
      slot: 0,
      atMillis: LOCAL_DAY * 86_400_000 + 10 * 60 * 60_000,
      ...overrides,
    };
  }

  // design §3 table row 1: "Streak-Risk near 20:00 suppresses Meditation Return same day".
  it('drops a same-day meditation_return candidate when a streak candidate exists', () => {
    const streakTask = task({ channel: 'streak' });
    const meditationReturnTask = task({ channel: 'meditation_return' });

    const result = applyPriority([streakTask, meditationReturnTask], ZONE);

    expect(result).toEqual([streakTask]);
  });

  it('drops a same-day meditation_return candidate when a healer candidate exists', () => {
    const healerTask = task({ channel: 'healer' });
    const meditationReturnTask = task({ channel: 'meditation_return' });

    const result = applyPriority([healerTask, meditationReturnTask], ZONE);

    expect(result).toEqual([healerTask]);
  });

  it('keeps meditation_return when neither streak nor healer is present', () => {
    const reminderTask = task({ channel: 'reminder' });
    const meditationReturnTask = task({ channel: 'meditation_return' });

    const result = applyPriority([reminderTask, meditationReturnTask], ZONE);

    expect(result).toEqual([reminderTask, meditationReturnTask]);
  });

  // design §3 table row 2 (plan-time half): mood <2h before reflection re-rolls later in the
  // segment, or drops the reflection slot entirely when there's no room left in the segment.
  it('re-rolls a reflection slot scheduled less than 2h after mood to later the same day', () => {
    const moodAt = LOCAL_DAY * 86_400_000 + 9 * 60 * 60_000; // 09:00 UTC (manana: 06:00-12:00)
    const moodTask = task({ channel: 'mood', atMillis: moodAt });
    const reflectionTask = task({ channel: 'reflection', atMillis: moodAt + 15 * 60_000 }); // 09:15

    const result = applyPriority([moodTask, reflectionTask], ZONE, () => 0);

    const rerolled = result.find((t) => t.channel === 'reflection');
    expect(rerolled).toBeDefined();
    expect(rerolled!.atMillis).toBeGreaterThanOrEqual(moodAt + 2 * 60 * 60_000);
    // Still inside manana (ends at 12:00 UTC) -- never spills into a later segment.
    expect(rerolled!.atMillis).toBeLessThan(LOCAL_DAY * 86_400_000 + 12 * 60 * 60_000);
  });

  it('drops the reflection slot when re-rolling would leave no room before local midnight', () => {
    const moodAt = LOCAL_DAY * 86_400_000 + 23 * 60 * 60_000; // 23:00 UTC, <1h before midnight (noche)
    const moodTask = task({ channel: 'mood', atMillis: moodAt });
    const reflectionTask = task({ channel: 'reflection', atMillis: moodAt + 15 * 60_000 });

    const result = applyPriority([moodTask, reflectionTask], ZONE, () => 0);

    expect(result.find((t) => t.channel === 'reflection')).toBeUndefined();
    expect(result).toContainEqual(moodTask);
  });

  // Bug fix (independent review, functions/planner.ts's applyPriority): re-rolling against local
  // midnight instead of the ORIGINAL reflection slot's own day-segment could push a morning
  // reflection slot all the way into the evening. It must now be clamped to that segment.
  it('clamps a morning reflection re-roll to the manana segment, never into the evening', () => {
    // manana = 06:00-12:00 UTC (schedule.ts's DAY_SEGMENTS). Mood at 11:00, reflection at 11:05:
    // the 2h floor (13:00) falls OUTSIDE manana (ends 12:00), so there is no room to re-roll into.
    const moodAt = LOCAL_DAY * 86_400_000 + 11 * 60 * 60_000;
    const moodTask = task({ channel: 'mood', atMillis: moodAt });
    const reflectionTask = task({ channel: 'reflection', atMillis: moodAt + 5 * 60_000 });

    const result = applyPriority([moodTask, reflectionTask], ZONE, () => 0);

    // Old (buggy) behavior would have re-rolled this into tarde/noche (up to near-midnight).
    // Correct behavior: the segment has no room left, so the slot is dropped, not pushed later.
    expect(result.find((t) => t.channel === 'reflection')).toBeUndefined();
    expect(result).toContainEqual(moodTask);
  });

  it('re-rolls within the tarde segment when mood is early afternoon, never into noche', () => {
    // tarde = 12:00-18:00 UTC. Mood at 12:30, reflection at 12:40: 2h floor is 14:30, well
    // inside tarde (ends 18:00) -- there IS room, so it re-rolls, but must stay before 18:00.
    const moodAt = LOCAL_DAY * 86_400_000 + 12 * 60 * 60_000 + 30 * 60_000;
    const moodTask = task({ channel: 'mood', atMillis: moodAt });
    const reflectionTask = task({ channel: 'reflection', atMillis: moodAt + 10 * 60_000 });

    const result = applyPriority([moodTask, reflectionTask], ZONE, () => 0);

    const rerolled = result.find((t) => t.channel === 'reflection');
    expect(rerolled).toBeDefined();
    expect(rerolled!.atMillis).toBeGreaterThanOrEqual(moodAt + 2 * 60 * 60_000);
    expect(rerolled!.atMillis).toBeLessThan(LOCAL_DAY * 86_400_000 + 18 * 60 * 60_000);
  });

  it('leaves a reflection slot untouched when it is more than 2h after mood', () => {
    const moodAt = LOCAL_DAY * 86_400_000 + 8 * 60 * 60_000;
    const moodTask = task({ channel: 'mood', atMillis: moodAt });
    const reflectionTask = task({ channel: 'reflection', atMillis: moodAt + 3 * 60 * 60_000 });

    const result = applyPriority([moodTask, reflectionTask], ZONE);

    expect(result).toContainEqual(reflectionTask);
  });

  it('leaves a reflection slot untouched when it is scheduled before mood', () => {
    const moodAt = LOCAL_DAY * 86_400_000 + 12 * 60 * 60_000;
    const moodTask = task({ channel: 'mood', atMillis: moodAt });
    const reflectionTask = task({ channel: 'reflection', atMillis: moodAt - 30 * 60_000 });

    const result = applyPriority([moodTask, reflectionTask], ZONE);

    expect(result).toContainEqual(reflectionTask);
  });

  it('is a no-op on an empty task list', () => {
    expect(applyPriority([], ZONE)).toEqual([]);
  });
});

describe('planUserTasks meditation_return candidate', () => {
  const EMPTY_MEDITATION_RETURN_STATE = {
    absenceStartLocalDay: null,
    lastSentLocalDay: null,
    lastBand: null,
  } as const;

  function baseMeditationReturnInput(localDay: number): UserPlanInput {
    return {
      ...baseInput,
      localDay,
      settings: {
        ...baseInput.settings,
        remindersEnabled: false,
        reflectionEnabled: false,
        moodEnabled: false,
      },
      meditationReturnState: EMPTY_MEDITATION_RETURN_STATE,
    };
  }

  it('enqueues a meditation_return task once the user crosses the inactive_3_4 band', async () => {
    const store = makeStore();
    const enqueuer = makeEnqueuer();
    const localDay = 20700;

    const input = {
      ...baseMeditationReturnInput(localDay),
      completions: [{ epochDay: localDay - 4, meditationDone: true, affirmationDone: false }],
    };

    await planUser(input, store, enqueuer);

    const meditationReturnTask = enqueuer.calls.find(
      (t) => (t as { channel: string }).channel === 'meditation_return',
    );
    expect(meditationReturnTask).toBeDefined();
  });

  it('does not enqueue a meditation_return task when the Meditation Return toggle is disabled', async () => {
    const store = makeStore();
    const enqueuer = makeEnqueuer();
    const localDay = 20700;

    const input = {
      ...baseMeditationReturnInput(localDay),
      settings: { ...baseMeditationReturnInput(localDay).settings, meditationReturnEnabled: false },
      completions: [{ epochDay: localDay - 4, meditationDone: true, affirmationDone: false }],
    };

    await planUser(input, store, enqueuer);

    const meditationReturnTask = enqueuer.calls.find(
      (t) => (t as { channel: string }).channel === 'meditation_return',
    );
    expect(meditationReturnTask).toBeUndefined();
  });

  it('does not enqueue a meditation_return task when the user meditated today', async () => {
    const store = makeStore();
    const enqueuer = makeEnqueuer();
    const localDay = 20700;

    const input = {
      ...baseMeditationReturnInput(localDay),
      completions: [{ epochDay: localDay, meditationDone: true, affirmationDone: false }],
    };

    await planUser(input, store, enqueuer);

    const meditationReturnTask = enqueuer.calls.find(
      (t) => (t as { channel: string }).channel === 'meditation_return',
    );
    expect(meditationReturnTask).toBeUndefined();
  });

  it('does not enqueue a second meditation_return task for the same band/absence already sent', async () => {
    const store = makeStore();
    const enqueuer = makeEnqueuer();
    const localDay = 20700;

    const input = {
      ...baseMeditationReturnInput(localDay),
      completions: [{ epochDay: localDay - 4, meditationDone: true, affirmationDone: false }],
      meditationReturnState: {
        absenceStartLocalDay: localDay - 3,
        lastSentLocalDay: localDay - 1,
        lastBand: 'inactive_3_4' as const,
      },
    };

    await planUser(input, store, enqueuer);

    const meditationReturnTask = enqueuer.calls.find(
      (t) => (t as { channel: string }).channel === 'meditation_return',
    );
    expect(meditationReturnTask).toBeUndefined();
  });

  it('a same-day streak candidate suppresses the meditation_return candidate end-to-end', async () => {
    const store = makeStore();
    const enqueuer = makeEnqueuer();
    const localDay = 20700;

    const input = {
      ...baseMeditationReturnInput(localDay),
      completions: [
        { epochDay: localDay - 4, meditationDone: true, affirmationDone: false },
        // Affirmation-only streak through yesterday (meditation still absent) -- fires
        // shouldFireStreakAlert without giving the user a fresh meditation day.
        { epochDay: localDay - 1, meditationDone: false, affirmationDone: true },
      ],
    };

    await planUser(input, store, enqueuer);

    const channels = enqueuer.calls.map((t) => (t as { channel: string }).channel);
    expect(channels).toContain('streak');
    expect(channels).not.toContain('meditation_return');
  });
});
