/**
 * Port of `DailyCompletionStats.streakOf` (app/src/main/java/com/pirxhio/affirmity/data/DailyCompletionStats.kt)
 * plus the server-side streak channel decisions (spec's "Streak-About-to-End Channel"
 * requirement): general streak, at-risk trigger, and the single general-streak alert selection.
 */

import type { HealerUse } from './healer';

export interface Completion {
  epochDay: number;
  meditationDone: boolean;
  affirmationDone: boolean;
}

/** Consecutive count of `isDone` days ending at `todayEpochDay` (walking backwards day by day). */
export function streakOf(
  rows: Completion[],
  todayEpochDay: number,
  isDone: (row: Completion) => boolean,
): number {
  const byDay = new Map(rows.map((row) => [row.epochDay, row]));
  let streak = 0;
  let day = todayEpochDay;
  while (true) {
    const row = byDay.get(day);
    if (!row || !isDone(row)) break;
    streak++;
    day--;
  }
  return streak;
}

/**
 * "Current streak" as shown to the user: the GENERAL streak, i.e. consecutive days ending at
 * `todayEpochDay` with at least one activity (meditation OR affirmations). Days may be held by
 * different activities. Healed days (`healerUses`) count as done, mirroring the app's
 * `StreakHealerStats.evaluate` (`effectiveDone = hasActivity || day in healedDays`).
 */
export function currentStreak(
  rows: Completion[],
  todayEpochDay: number,
  healerUses: HealerUse[] = [],
): number {
  const healedDays = new Set(healerUses.map((use) => use.healedEpochDay));
  const byDay = new Map(rows.map((row) => [row.epochDay, row]));
  let streak = 0;
  // A healed day may have no completion row at all, so walk days rather than rows.
  for (let day = todayEpochDay; ; day--) {
    const row = byDay.get(day);
    if (!(row?.meditationDone || row?.affirmationDone || healedDays.has(day))) break;
    streak++;
  }
  return streak;
}

export interface StreakAlertSelection {
  /** The GENERAL streak (activity on either channel) through yesterday. */
  streakCount: number;
  /** Meditation's own streak through yesterday (0 when it did not happen yesterday). */
  meditationStreak: number;
  /** Affirmations' own streak through yesterday (0 when it did not happen yesterday). */
  affirmationsStreak: number;
}

/**
 * Streak-about-to-end trigger condition: the GENERAL streak is live through yesterday AND there
 * is no activity yet on `todayEpochDay`. Basing the at-risk count on yesterday is essential:
 * early-morning planning normally runs before today's first completion.
 */
export function shouldFireStreakAlert(
  rows: Completion[],
  todayEpochDay: number,
  healerUses: HealerUse[] = [],
): boolean {
  const today = rows.find((row) => row.epochDay === todayEpochDay);
  const activeToday = (today?.affirmationDone ?? false) || (today?.meditationDone ?? false);

  // Derived from `selectStreakAlert` (non-null iff the general streak through yesterday is live),
  // so the trigger and the selection cannot drift apart.
  return selectStreakAlert(rows, todayEpochDay, healerUses) !== null && !activeToday;
}

/**
 * Builds the single streak alert: always about the GENERAL streak, plus each activity's own streak
 * through yesterday so the copy can explain what is holding it. Returns null when there is no live
 * general streak through yesterday. Per-activity streaks are activity-only (healed days do not
 * count), so both can be 0 when yesterday was only healed -- see `hasStreakBreakdown`.
 */
export function selectStreakAlert(
  rows: Completion[],
  todayEpochDay: number,
  healerUses: HealerUse[] = [],
): StreakAlertSelection | null {
  const yesterday = todayEpochDay - 1;
  const general = currentStreak(rows, yesterday, healerUses);
  if (general < 1) return null;

  return {
    streakCount: general,
    meditationStreak: streakOf(rows, yesterday, (row) => row.meditationDone),
    affirmationsStreak: streakOf(rows, yesterday, (row) => row.affirmationDone),
  };
}

/**
 * The streak a healer notification protects/recovers on `localDay`: the general streak held
 * through the break day (`localDay - 1`), i.e. as of `localDay - 2`, counting earlier heals.
 */
export function healerProtectedStreak(rows: Completion[], localDay: number, healerUses: HealerUse[] = []): number {
  return currentStreak(rows, localDay - 2, healerUses);
}

/** False when yesterday was only healed (no activity), so no per-activity breakdown copy applies. */
export function hasStreakBreakdown(selection: StreakAlertSelection): boolean {
  return selection.meditationStreak > 0 || selection.affirmationsStreak > 0;
}

export type StreakBreakdownContext = 'streak_both' | 'streak_meditation_only' | 'streak_affirmations_only';

/** Copy-context tag for which activities currently hold the general streak (never prints "0 days"). */
export function streakBreakdownContext(selection: StreakAlertSelection): StreakBreakdownContext {
  // Callers must check `hasStreakBreakdown` first: both streaks being 0 happens when yesterday was
  // only healed. Fail loudly rather than silently tagging an arbitrary breakdown.
  if (selection.meditationStreak <= 0 && selection.affirmationsStreak <= 0) {
    throw new Error('streakBreakdownContext: both activity streaks are 0 (check hasStreakBreakdown first)');
  }
  if (selection.meditationStreak > 0 && selection.affirmationsStreak > 0) return 'streak_both';
  return selection.meditationStreak > 0 ? 'streak_meditation_only' : 'streak_affirmations_only';
}

export type StreakBand = 'streak_1_3' | 'streak_4_13' | 'streak_14plus';

/**
 * Copy-context band for a live streak count (design §1/File Changes, `notification-copy-catalog`'s
 * "Context Filtering of Variant Pools" requirement). Only meaningful for `count >= 1` --
 * `shouldFireStreakAlert` never fires with a zero streak, so no band is defined for it here.
 */
export function streakBand(count: number): StreakBand {
  if (count >= 14) return 'streak_14plus';
  if (count >= 4) return 'streak_4_13';
  return 'streak_1_3';
}
