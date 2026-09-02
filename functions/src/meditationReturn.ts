/**
 * Meditation-return inactivity signal (design §4, `meditation-return` capability). Low-pressure
 * re-engagement after real meditation inactivity -- not a streak warning. Pure, Vitest-testable
 * core; Firestore wiring (reading `notificationState/current.meditationReturn`, writing back
 * `nextState` on a successful send) lives in `index.ts`, matching `streak.ts`/`healer.ts`'s
 * pure-core convention.
 *
 * Data source: `users/{uid}/dailyCompletions/{epochDay}.meditationDone` -- there is no dedicated
 * meditation-session collection in this codebase, so completions are the only meditation history.
 */

import type { Completion } from './streak';

export const MEDITATION_RETURN_ALERT_MINUTE = 19 * 60; // 19:00 user-local time
export const MEDITATION_RETURN_LOOKBACK_DAYS = 30;
export const MEDITATION_RETURN_COOLDOWN_DAYS = 7;

export type MeditationReturnBand = 'inactive_3_4' | 'inactive_7_10';

/** Per-user cooldown state (design §2's `NotificationStateDoc.meditationReturn`). */
export interface MeditationReturnState {
  /** First day of the absence this state describes; stale state (a different absence) is ignored. */
  absenceStartLocalDay: number | null;
  lastSentLocalDay: number | null;
  lastBand: MeditationReturnBand | null;
}

/**
 * Full local days with no `meditationDone`, counting back from `todayEpochDay - 1`. Returns `null`
 * when the user meditated today or has no meditation history within `lookbackDays`.
 */
export function daysSinceLastMeditation(
  rows: Completion[],
  todayEpochDay: number,
  lookbackDays: number = MEDITATION_RETURN_LOOKBACK_DAYS,
): number | null {
  const byDay = new Map(rows.map((row) => [row.epochDay, row]));
  if (byDay.get(todayEpochDay)?.meditationDone) return null;

  const earliestDay = todayEpochDay - lookbackDays;
  for (let day = todayEpochDay - 1; day >= earliestDay; day--) {
    if (byDay.get(day)?.meditationDone) {
      return todayEpochDay - 1 - day;
    }
  }
  return null;
}

/** `3..4` -> `'inactive_3_4'`; `7..10` -> `'inactive_7_10'`; everything else -> `null` (silence). */
export function meditationReturnBand(inactiveDays: number): MeditationReturnBand | null {
  if (inactiveDays >= 3 && inactiveDays <= 4) return 'inactive_3_4';
  if (inactiveDays >= 7 && inactiveDays <= 10) return 'inactive_7_10';
  return null;
}

export interface MeditationReturnDecision {
  fire: boolean;
  band?: MeditationReturnBand;
  inactiveDays?: number;
  nextState?: MeditationReturnState;
}

/**
 * design §4 decision rules, in order:
 *  1. Meditated today -> false.
 *  2. No band for the current inactivity -> false.
 *  3. `absenceStart = todayEpochDay - inactiveDays`; if `state.absenceStartLocalDay` describes a
 *     different (earlier, already-resolved) absence, treat the stored state as empty -- this is
 *     how a completed meditation resets the cooldown, with no write needed.
 *  4. Same band already sent for this absence -> false (one send per band per absence).
 *  5. Last send was less than `MEDITATION_RETURN_COOLDOWN_DAYS` ago -> false. Together, (4) and
 *     (5) guarantee at most 2 notifications per absence, >= 7 days apart -- "never daily nagging".
 */
export function shouldFireMeditationReturn(
  rows: Completion[],
  todayEpochDay: number,
  state: MeditationReturnState,
): MeditationReturnDecision {
  const inactiveDays = daysSinceLastMeditation(rows, todayEpochDay);
  if (inactiveDays === null) return { fire: false };

  const band = meditationReturnBand(inactiveDays);
  if (band === null) return { fire: false };

  const absenceStartLocalDay = todayEpochDay - inactiveDays;
  const effectiveState: MeditationReturnState =
    state.absenceStartLocalDay === absenceStartLocalDay
      ? state
      : { absenceStartLocalDay: null, lastSentLocalDay: null, lastBand: null };

  if (effectiveState.lastBand === band) return { fire: false };

  if (
    effectiveState.lastSentLocalDay !== null &&
    todayEpochDay - effectiveState.lastSentLocalDay < MEDITATION_RETURN_COOLDOWN_DAYS
  ) {
    return { fire: false };
  }

  return {
    fire: true,
    band,
    inactiveDays,
    nextState: { absenceStartLocalDay, lastSentLocalDay: todayEpochDay, lastBand: band },
  };
}
