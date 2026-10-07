/**
 * Port of `DailyCompletionStats.streakOf` (app/src/main/java/com/pirxhio/affirmity/data/DailyCompletionStats.kt)
 * plus the server-side streak channel decisions (spec's "Streak-About-to-End Channel"
 * requirement): general streak, at-risk trigger, and the single general-streak alert selection.
 */

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
 * different activities.
 */
export function currentStreak(rows: Completion[], todayEpochDay: number): number {
  return streakOf(rows, todayEpochDay, (row) => row.meditationDone || row.affirmationDone);
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
export function shouldFireStreakAlert(rows: Completion[], todayEpochDay: number): boolean {
  const today = rows.find((row) => row.epochDay === todayEpochDay);
  const activeToday = (today?.affirmationDone ?? false) || (today?.meditationDone ?? false);

  // Derived from `selectStreakAlert` (non-null iff the general streak through yesterday is live),
  // so the trigger and the selection cannot drift apart.
  return selectStreakAlert(rows, todayEpochDay) !== null && !activeToday;
}

/**
 * Builds the single streak alert: always about the GENERAL streak, plus each activity's own streak
 * through yesterday so the copy can explain what is holding it. Returns null when there is no live
 * general streak through yesterday. At least one per-activity streak is >= 1 when non-null.
 */
export function selectStreakAlert(rows: Completion[], todayEpochDay: number): StreakAlertSelection | null {
  const yesterday = todayEpochDay - 1;
  const general = currentStreak(rows, yesterday);
  if (general < 1) return null;

  return {
    streakCount: general,
    meditationStreak: streakOf(rows, yesterday, (row) => row.meditationDone),
    affirmationsStreak: streakOf(rows, yesterday, (row) => row.affirmationDone),
  };
}

export type StreakBreakdownContext = 'streak_both' | 'streak_meditation_only' | 'streak_affirmations_only';

/** Copy-context tag for which activities currently hold the general streak (never prints "0 days"). */
export function streakBreakdownContext(selection: StreakAlertSelection): StreakBreakdownContext {
  // A live general streak through yesterday means at least one activity was done yesterday, so its
  // own streak is >= 1. Both being 0 is impossible for a `selectStreakAlert` result: fail loudly
  // rather than silently tagging an arbitrary breakdown.
  if (selection.meditationStreak <= 0 && selection.affirmationsStreak <= 0) {
    throw new Error('streakBreakdownContext: impossible state, both activity streaks are 0 with a live general streak');
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
