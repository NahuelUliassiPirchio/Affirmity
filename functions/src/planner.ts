/**
 * Nightly (hourly-tick) planner pass: for one active user, compute reminder/reflection slot
 * instants (schedule.ts) and the streak-about-to-end decision (streak.ts), then enqueue one Cloud
 * Task per computed instant. Idempotent per `{uid, localDay}` via `PlanStore.hasPlan`; failures
 * for one user never abort the pass (`planAllUsers`'s per-user try/catch).
 */

import { subWindow, slotInstant } from './schedule';
import { shouldFireStreakAlert, type Completion } from './streak';

export const REMINDER_SLOT_COUNT = 3;
export const REFLECTION_SLOT_COUNT = 3;
export const STREAK_ALERT_MINUTE = 20 * 60; // 20:00 user-local time

export type NotificationChannel = 'reminder' | 'reflection' | 'streak';

export interface NotificationSettings {
  remindersEnabled: boolean;
  reflectionEnabled: boolean;
  reminderStartMinute: number;
  reminderEndMinute: number;
  reflectionStartMinute: number;
  reflectionEndMinute: number;
  timeZone: string | null;
}

export interface UserPlanInput {
  uid: string;
  localDay: number;
  settings: NotificationSettings;
  completions: Completion[];
}

export interface PlannedTask {
  uid: string;
  localDay: number;
  channel: NotificationChannel;
  slot: number;
  atMillis: number;
}

export type PlanStatus = 'planned' | 'skipped' | 'failed';

export interface PlanResult {
  uid: string;
  localDay: number;
  tasks: PlannedTask[];
  status: PlanStatus;
  error?: string;
}

export interface PlanStore {
  hasPlan(uid: string, localDay: number): Promise<boolean>;
  markPlanned(uid: string, localDay: number, result: PlanResult): Promise<void>;
  markFailed(uid: string, localDay: number, error: string): Promise<void>;
}

export interface TaskEnqueuer {
  enqueue(task: PlannedTask): Promise<{ created: boolean }>;
}

function windowSlots(
  localDay: number,
  zone: string,
  startMinute: number,
  endMinute: number,
  slotCount: number,
  channel: NotificationChannel,
  rng: () => number,
): PlannedTask[] {
  const tasks: PlannedTask[] = [];
  for (let i = 0; i < slotCount; i++) {
    const [subStart, subEnd] = subWindow(startMinute, endMinute, i, slotCount);
    const instant = slotInstant(localDay, zone, subStart, subEnd, rng);
    tasks.push({ uid: '', localDay, channel, slot: i, atMillis: instant.getTime() });
  }
  return tasks;
}

/** Pure: computes this user's tasks for one local day. No I/O. */
export function planUserTasks(input: UserPlanInput, rng: () => number = Math.random): PlannedTask[] {
  const { settings, completions, localDay, uid } = input;
  const zone = settings.timeZone;
  if (!zone) return [];

  const tasks: PlannedTask[] = [];

  if (settings.remindersEnabled) {
    tasks.push(
      ...windowSlots(
        localDay,
        zone,
        settings.reminderStartMinute,
        settings.reminderEndMinute,
        REMINDER_SLOT_COUNT,
        'reminder',
        rng,
      ),
    );
  }

  if (settings.reflectionEnabled) {
    tasks.push(
      ...windowSlots(
        localDay,
        zone,
        settings.reflectionStartMinute,
        settings.reflectionEndMinute,
        REFLECTION_SLOT_COUNT,
        'reflection',
        rng,
      ),
    );
  }

  if (shouldFireStreakAlert(completions, localDay)) {
    tasks.push({
      uid: '',
      localDay,
      channel: 'streak',
      slot: 0,
      atMillis: slotInstant(localDay, zone, STREAK_ALERT_MINUTE, STREAK_ALERT_MINUTE, rng).getTime(),
    });
  }

  return tasks.map((task) => ({ ...task, uid }));
}

/** Plans and enqueues one user's tasks for `input.localDay`; idempotent per `{uid, localDay}`. */
export async function planAndEnqueueUser(
  input: UserPlanInput,
  store: PlanStore,
  enqueuer: TaskEnqueuer,
  rng: () => number = Math.random,
): Promise<PlanResult> {
  const alreadyPlanned = await store.hasPlan(input.uid, input.localDay);
  if (alreadyPlanned) {
    return { uid: input.uid, localDay: input.localDay, tasks: [], status: 'skipped' };
  }

  const tasks = planUserTasks(input, rng);
  for (const task of tasks) {
    await enqueuer.enqueue(task);
  }

  const result: PlanResult = { uid: input.uid, localDay: input.localDay, tasks, status: 'planned' };
  await store.markPlanned(input.uid, input.localDay, result);
  return result;
}

/** Plans all given users; one user's failure is isolated and never aborts the rest of the pass. */
export async function planAllUsers(
  inputs: UserPlanInput[],
  store: PlanStore,
  enqueuer: TaskEnqueuer,
  rng: () => number = Math.random,
): Promise<PlanResult[]> {
  const results: PlanResult[] = [];
  for (const input of inputs) {
    try {
      results.push(await planAndEnqueueUser(input, store, enqueuer, rng));
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      try {
        await store.markFailed(input.uid, input.localDay, message);
      } catch {
        // Best-effort marker write; the failure is already captured in the returned result.
      }
      results.push({ uid: input.uid, localDay: input.localDay, tasks: [], status: 'failed', error: message });
    }
  }
  return results;
}
