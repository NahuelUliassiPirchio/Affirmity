/**
 * Port of the streak-healer "held"/"Available" derivation from
 * `StreakHealerStats.evaluate` (app/src/main/java/com/pirxhio/affirmity/data/StreakHealerStats.kt)
 * -- only the subset needed for the "healer available" notification decision: whether `todayEpochDay`
 * is the single day right after a break, a healer is held, and it hasn't been activated yet.
 */

import type { Completion } from './streak';

export interface HealerUse {
  healedEpochDay: number;
}

/** How far back to look when deriving healer eligibility -- mirrors `StreakHealerStats.LOOKBACK_DAYS`. */
export const HEALER_LOOKBACK_DAYS = 370;

/** This feature's release day (2026-08-04, UTC-calendar-aligned epoch day) -- mirrors
 * `StreakHealerStats.EPOCH_START_DAY`. Completion history from before it can never retroactively
 * grant a healer. */
export const HEALER_EPOCH_START_DAY = Math.floor(Date.UTC(2026, 7, 4) / 86_400_000);

/** Floors the usual [HEALER_LOOKBACK_DAYS] window at [HEALER_EPOCH_START_DAY]. */
export function healerStartEpochDay(todayEpochDay: number): number {
  return Math.max(todayEpochDay - HEALER_LOOKBACK_DAYS, HEALER_EPOCH_START_DAY);
}

/**
 * Streak-healer-available trigger condition: `todayEpochDay - 1` broke the streak (zero activity,
 * with an alive day or the window floor right before it), a healer was held going into that break,
 * and it has not already been activated to heal that specific break day.
 */
export function shouldFireHealerAlert(rows: Completion[], uses: HealerUse[], todayEpochDay: number): boolean {
  const startEpochDay = healerStartEpochDay(todayEpochDay);
  const byDay = new Map(rows.map((row) => [row.epochDay, row]));
  const hasActivity = (day: number) => {
    const row = byDay.get(day);
    return row ? row.meditationDone || row.affirmationDone : false;
  };
  const isFullDay = (day: number) => {
    const row = byDay.get(day);
    return row ? row.meditationDone && row.affirmationDone : false;
  };
  const healedDays = new Set(uses.map((use) => use.healedEpochDay));
  const effectiveDone = (day: number) => hasActivity(day) || healedDays.has(day);

  let held = false;
  let fullDayStreak = 0;
  for (let day = startEpochDay; day <= todayEpochDay; day++) {
    fullDayStreak = isFullDay(day) ? fullDayStreak + 1 : 0;
    if (fullDayStreak >= 2 && !held) held = true;
    if (healedDays.has(day)) held = false;
  }

  const breakCandidate = todayEpochDay - 1;
  const isBreakDay =
    breakCandidate >= startEpochDay &&
    !hasActivity(breakCandidate) &&
    (breakCandidate === startEpochDay || effectiveDone(breakCandidate - 1));

  return isBreakDay && held && !healedDays.has(breakCandidate);
}
