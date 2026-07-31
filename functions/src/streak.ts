/**
 * Port of `DailyCompletionStats.streakOf` (app/src/main/java/com/pirxhio/affirmity/data/DailyCompletionStats.kt)
 * plus `shouldFireStreakAlert`, the new server-side streak channel decision (spec's
 * "Streak-About-to-End Channel" requirement).
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
 * Streak-about-to-end trigger condition (proposal decision 3, spec's "Streak-About-to-End
 * Channel"): the user's current streak is >= 1 AND at least one of affirmation/meditation is
 * still unmarked for `todayEpochDay`. "Current streak" is the longer of the two independently
 * tracked (meditation, affirmation) contiguous streaks -- the app tracks and displays them
 * separately (see `AffirmityAppState.affirmationsStreak` / `.meditationStreak`), and the spec
 * scenarios describe a single undifferentiated "streak of N" without picking one track, so this
 * takes the max of both as the more conservative (fires more readily) interpretation.
 */
export function shouldFireStreakAlert(rows: Completion[], todayEpochDay: number): boolean {
  const meditationStreak = streakOf(rows, todayEpochDay, (row) => row.meditationDone);
  const affirmationStreak = streakOf(rows, todayEpochDay, (row) => row.affirmationDone);
  const streak = Math.max(meditationStreak, affirmationStreak);

  const today = rows.find((row) => row.epochDay === todayEpochDay);
  const affirmationDone = today?.affirmationDone ?? false;
  const meditationDone = today?.meditationDone ?? false;

  return streak >= 1 && (!affirmationDone || !meditationDone);
}
