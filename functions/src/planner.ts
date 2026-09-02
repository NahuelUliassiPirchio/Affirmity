/**
 * Nightly (hourly-tick) planner pass: for one active user, compute reminder/reflection slot
 * instants (schedule.ts) and the streak-about-to-end decision (streak.ts), then enqueue one Cloud
 * Task per computed instant. Idempotent per `{uid, localDay}` via `PlanStore.hasPlan`; failures
 * for one user never abort the pass (`planAllUsers`'s per-user try/catch).
 */

import type { NotificationFamily } from './copyCatalog';
import { localInstantMillis, localMidnightUtcMillis, localMinuteOfDay } from './localDay';
import {
  MEDITATION_RETURN_ALERT_MINUTE,
  shouldFireMeditationReturn,
  type MeditationReturnState,
} from './meditationReturn';
import { DAY_SEGMENTS, isSafelyFuture, isWithinQuietHours, segmentSlots, slotInstant } from './schedule';
import { currentStreak, shouldFireStreakAlert, type Completion } from './streak';
import { shouldFireHealerAlert, type HealerUse } from './healer';

export const REMINDER_SLOT_COUNT = 3;
export const REFLECTION_SLOT_COUNT = 3;
export const MOOD_SLOT_COUNT = 1;
export const STREAK_ALERT_MINUTE = 20 * 60; // 20:00 user-local time
export const HEALER_ALERT_MINUTE = 20 * 60; // 20:00 user-local time

export type NotificationChannel =
  | 'reminder'
  | 'reflection'
  | 'mood'
  | 'streak'
  | 'healer'
  | 'meditation_return';

export interface NotificationSettings {
  remindersEnabled: boolean;
  reflectionEnabled: boolean;
  moodEnabled: boolean;
  /** Settings Toggles requirement -- default-true handling lives at the `index.ts` read site
   *  (D8), not here; this interface just carries the already-resolved value. */
  streakEnabled: boolean;
  healerEnabled: boolean;
  meditationReturnEnabled: boolean;
  reminderSegments: string[];
  reflectionSegments: string[];
  moodSegments: string[];
  quietHoursEnabled: boolean;
  quietHoursStartMinute: number;
  quietHoursEndMinute: number;
  timeZone: string | null;
}

export interface UserPlanInput {
  uid: string;
  localDay: number;
  settings: NotificationSettings;
  completions: Completion[];
  healerUses: HealerUse[];
  meditationReturnState: MeditationReturnState;
}

export interface PlannedTask {
  uid: string;
  localDay: number;
  channel: NotificationChannel;
  slot: number;
  atMillis: number;
  /** Structured values the Android client localizes when rendering the notification. */
  data?: NotificationData;
}

export interface NotificationData {
  streakCount?: string;
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

/** Pure: computes this user's tasks for one local day. No I/O. */
export function planUserTasks(
  input: UserPlanInput,
  rng: () => number = Math.random,
  planGeneratedAtMillis: number = Date.now(),
): PlannedTask[] {
  const { settings, completions, healerUses, meditationReturnState, localDay, uid } = input;
  const zone = settings.timeZone;
  if (!zone) return [];

  const tasks: PlannedTask[] = [];

  if (settings.remindersEnabled) {
    tasks.push(
      ...segmentSlots(
        localDay,
        zone,
        settings.reminderSegments,
        REMINDER_SLOT_COUNT,
        'reminder',
        rng,
        planGeneratedAtMillis,
      ),
    );
  }

  if (settings.reflectionEnabled) {
    tasks.push(
      ...segmentSlots(
        localDay,
        zone,
        settings.reflectionSegments,
        REFLECTION_SLOT_COUNT,
        'reflection',
        rng,
        planGeneratedAtMillis,
      ),
    );
  }

  if (settings.moodEnabled) {
    tasks.push(
      ...segmentSlots(
        localDay,
        zone,
        settings.moodSegments,
        MOOD_SLOT_COUNT,
        'mood',
        rng,
        planGeneratedAtMillis,
      ),
    );
  }

  if (settings.streakEnabled && shouldFireStreakAlert(completions, localDay)) {
    const streak = currentStreak(completions, localDay - 1);
    const task: PlannedTask = {
      uid: '',
      localDay,
      channel: 'streak',
      slot: 0,
      atMillis: slotInstant(localDay, zone, STREAK_ALERT_MINUTE, STREAK_ALERT_MINUTE, rng).getTime(),
      data: { streakCount: String(streak) },
    };
    if (isSafelyFuture(task.atMillis, planGeneratedAtMillis)) tasks.push(task);
  }

  if (settings.healerEnabled && shouldFireHealerAlert(completions, healerUses, localDay)) {
    const task: PlannedTask = {
      uid: '',
      localDay,
      channel: 'healer',
      slot: 0,
      atMillis: slotInstant(localDay, zone, HEALER_ALERT_MINUTE, HEALER_ALERT_MINUTE, rng).getTime(),
    };
    if (isSafelyFuture(task.atMillis, planGeneratedAtMillis)) tasks.push(task);
  }

  const meditationReturnDecision = settings.meditationReturnEnabled
    ? shouldFireMeditationReturn(completions, localDay, meditationReturnState)
    : { fire: false as const };
  if (meditationReturnDecision.fire) {
    const task: PlannedTask = {
      uid: '',
      localDay,
      channel: 'meditation_return',
      slot: 0,
      atMillis: slotInstant(
        localDay,
        zone,
        MEDITATION_RETURN_ALERT_MINUTE,
        MEDITATION_RETURN_ALERT_MINUTE,
        rng,
      ).getTime(),
    };
    if (isSafelyFuture(task.atMillis, planGeneratedAtMillis)) tasks.push(task);
  }

  const filtered = settings.quietHoursEnabled
    ? tasks.filter((task) => !isWithinQuietHours(
        localMinuteOfDay(task.atMillis, zone),
        settings.quietHoursStartMinute,
        settings.quietHoursEndMinute,
      ))
    : tasks;

  return applyPriority(filtered, zone, rng).map((task) => ({ ...task, uid }));
}

/**
 * Plan-time cross-family priority order (design §3 suppression/priority decision table,
 * `notification-orchestration`'s "Plan-Time Cross-Family Priority and Suppression" requirement).
 */
export const FAMILY_PRIORITY: NotificationFamily[] = [
  'streak',
  'healer',
  'mood',
  'reflection',
  'reminder',
  'meditation_return',
];

/** design §3 row: "Mood <2h ago postpones/suppresses Compass" -- plan-time half. */
const REFLECTION_MOOD_MIN_GAP_MS = 2 * 60 * 60_000;

/**
 * The end-of-segment instant (local millis) for `minuteOfDay` on `localDay`/`zone`, per
 * `schedule.ts`'s `DAY_SEGMENTS` table. Falls back to local midnight of the NEXT day when
 * `minuteOfDay` doesn't land in any known segment (defensive; every real slot instant does).
 */
function segmentEndMillisFor(minuteOfDay: number, localDay: number, zone: string): number {
  const segment = Object.values(DAY_SEGMENTS).find(
    (range) => minuteOfDay >= range.startMinute && minuteOfDay < range.endMinute,
  );
  const endMinute = segment?.endMinute ?? 1440;
  return endMinute >= 1440 ? localMidnightUtcMillis(localDay + 1, zone) : localInstantMillis(localDay, zone, endMinute);
}

/**
 * Applies design §3's plan-time suppression rules to one user's already-quiet-hours-filtered
 * candidate tasks for a local day:
 *  1. A `streak` or `healer` candidate suppresses a same-day `meditation_return` candidate
 *     outright.
 *  2. A `reflection` slot scheduled less than 2h after a `mood` slot is re-rolled to a random
 *     instant later THE SAME DAY-SEGMENT the original slot fell into (>= the 2h floor, < that
 *     segment's own end) -- never spilling into a later segment -- or dropped entirely if the
 *     2h floor leaves no room before that segment ends. Bug fix (independent review): re-rolling
 *     against local midnight instead of the original segment's end could push e.g. a `manana`
 *     (morning) reflection slot all the way into the `noche` (evening) segment.
 */
export function applyPriority(
  tasks: PlannedTask[],
  zone: string,
  rng: () => number = Math.random,
): PlannedTask[] {
  if (tasks.length === 0) return tasks;
  const localDay = tasks[0].localDay;

  const hasHigherPriorityAlert = tasks.some((task) => task.channel === 'streak' || task.channel === 'healer');
  let result = hasHigherPriorityAlert
    ? tasks.filter((task) => task.channel !== 'meditation_return')
    : tasks;

  const moodTask = result.find((task) => task.channel === 'mood');
  if (moodTask) {
    result = result.flatMap((task) => {
      if (task.channel !== 'reflection') return [task];
      const gap = task.atMillis - moodTask.atMillis;
      if (!(gap > 0 && gap < REFLECTION_MOOD_MIN_GAP_MS)) return [task];

      const originalMinuteOfDay = localMinuteOfDay(task.atMillis, zone);
      const segmentEndMillis = segmentEndMillisFor(originalMinuteOfDay, localDay, zone);

      const earliest = moodTask.atMillis + REFLECTION_MOOD_MIN_GAP_MS;
      if (earliest >= segmentEndMillis) return [];

      const span = segmentEndMillis - earliest;
      const rerolledAtMillis = earliest + Math.min(Math.floor(rng() * span), span - 1);
      return [{ ...task, atMillis: rerolledAtMillis }];
    });
  }

  return result;
}

/** Plans and enqueues one user's tasks for `input.localDay`; idempotent per `{uid, localDay}`. */
export async function planAndEnqueueUser(
  input: UserPlanInput,
  store: PlanStore,
  enqueuer: TaskEnqueuer,
  rng: () => number = Math.random,
  planGeneratedAtMillis: number = Date.now(),
): Promise<PlanResult> {
  const alreadyPlanned = await store.hasPlan(input.uid, input.localDay);
  if (alreadyPlanned) {
    return { uid: input.uid, localDay: input.localDay, tasks: [], status: 'skipped' };
  }

  const tasks = planUserTasks(input, rng, planGeneratedAtMillis);
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
  planGeneratedAtMillis?: number,
): Promise<PlanResult[]> {
  const results: PlanResult[] = [];
  for (const input of inputs) {
    try {
      const userPlanGeneratedAtMillis = planGeneratedAtMillis ?? Date.now();
      results.push(await planAndEnqueueUser(input, store, enqueuer, rng, userPlanGeneratedAtMillis));
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      // Visible in Cloud Functions logs, not just the Firestore marker doc -- a silent catch here
      // is exactly what made an IAM permission gap undiagnosable from `firebase functions:log`
      // during this stage's rollout.
      console.error(`planAllUsers: failed for uid=${input.uid} localDay=${input.localDay}: ${message}`);
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
