import { describe, expect, it, vi } from 'vitest';

import {
  planAllUsers,
  planAndEnqueueUser,
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
    reminderStartMinute: 9 * 60,
    reminderEndMinute: 21 * 60,
    reflectionStartMinute: 0,
    reflectionEndMinute: 0,
    timeZone: 'UTC',
  },
  completions: [],
  healerUses: [],
};

describe('planAndEnqueueUser', () => {
  it('enqueues 3 reminder tasks and marks the plan as planned', async () => {
    const store = makeStore();
    const enqueuer = makeEnqueuer();

    const result = await planAndEnqueueUser(baseInput, store, enqueuer);

    expect(result.status).toBe('planned');
    expect(enqueuer.calls).toHaveLength(3);
    expect(store.plans.has('user-1-19000')).toBe(true);
  });

  // Design.md: "Planner idempotency ... a second run for the same localDay enqueues nothing".
  it('a second run for the same localDay enqueues nothing (idempotent)', async () => {
    const store = makeStore();
    const enqueuer = makeEnqueuer();

    await planAndEnqueueUser(baseInput, store, enqueuer);
    const second = await planAndEnqueueUser(baseInput, store, enqueuer);

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

    const results = await planAllUsers(inputs, store, failingEnqueuer);

    expect(results.find((r) => r.uid === 'bad-user')?.status).toBe('failed');
    expect(results.find((r) => r.uid === 'good-user')?.status).toBe('planned');
    expect(store.markFailed).toHaveBeenCalledWith('bad-user', 19000, expect.any(String));
  });

  it('includes the current streak count in the streak task body', async () => {
    const store = makeStore();
    const enqueuer = makeEnqueuer();
    const localDay = baseInput.localDay;

    const input: UserPlanInput = {
      ...baseInput,
      completions: [
        { epochDay: localDay - 1, meditationDone: true, affirmationDone: true },
        { epochDay: localDay, meditationDone: true, affirmationDone: false },
      ],
    };

    await planAndEnqueueUser(input, store, enqueuer);

    const streakTask = enqueuer.calls.find((task) => (task as { channel: string }).channel === 'streak') as
      | { body?: string }
      | undefined;
    expect(streakTask?.body).toBe('Llevás una racha de 2 días. Todavía puedes completar hoy y no perderla.');
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

    await planAndEnqueueUser(input, store, enqueuer);

    const healerTask = enqueuer.calls.find((task) => (task as { channel: string }).channel === 'healer');
    expect(healerTask).toBeDefined();
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

    await planAndEnqueueUser(input, store, enqueuer);

    const healerTask = enqueuer.calls.find((task) => (task as { channel: string }).channel === 'healer');
    expect(healerTask).toBeUndefined();
  });

  it('plans nothing for a user missing a timezone', async () => {
    const store = makeStore();
    const enqueuer = makeEnqueuer();

    const results = await planAllUsers(
      [{ ...baseInput, settings: { ...baseInput.settings, timeZone: null } }],
      store,
      enqueuer,
    );

    expect(results[0].status).toBe('planned');
    expect(results[0].tasks).toHaveLength(0);
    expect(enqueuer.calls).toHaveLength(0);
  });
});
