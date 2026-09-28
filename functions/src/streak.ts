/**
 * Port of `DailyCompletionStats.streakOf` (app/src/main/java/com/pirxhio/affirmity/data/DailyCompletionStats.kt)
 * plus the server-side streak channel decisions (spec's "Streak-About-to-End Channel"
 * requirement): general streak, at-risk trigger, and general-vs-activity alert selection.
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

export type StreakActivity = 'meditation' | 'affirmations';

/** Minimum own-streak length for an activity-specific streak notification. */
export const ACTIVITY_STREAK_MIN_DAYS = 3;

export interface StreakAlertSelection {
  streakCount: number;
  /** Present only for an activity-specific alert. */
  activity?: StreakActivity;
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
 * Picks what the streak alert talks about. Defaults to the general streak; switches to an
 * activity-specific alert only when exactly ONE activity has its own live streak of
 * `ACTIVITY_STREAK_MIN_DAYS`+ days through yesterday (when both do, the general streak already
 * describes the user). Returns null when there is no live general streak through yesterday.
 */
export function selectStreakAlert(rows: Completion[], todayEpochDay: number): StreakAlertSelection | null {
  const yesterday = todayEpochDay - 1;
  const general = currentStreak(rows, yesterday);
  if (general < 1) return null;

  const meditation = streakOf(rows, yesterday, (row) => row.meditationDone);
  const affirmations = streakOf(rows, yesterday, (row) => row.affirmationDone);
  const meditationLive = meditation >= ACTIVITY_STREAK_MIN_DAYS;
  const affirmationsLive = affirmations >= ACTIVITY_STREAK_MIN_DAYS;

  if (meditationLive && !affirmationsLive) return { streakCount: meditation, activity: 'meditation' };
  if (affirmationsLive && !meditationLive) return { streakCount: affirmations, activity: 'affirmations' };
  return { streakCount: general };
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
