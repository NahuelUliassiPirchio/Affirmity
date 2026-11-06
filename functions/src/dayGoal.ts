/**
 * Server mirror of the client's `DayRiskEvaluator` GoalIncomplete state: the day's goal needs ONE
 * of two activities (meditation, affirmations). When exactly one is done, the other is "missing"
 * and worth a gentle end-of-day nudge. Both done (goal complete) or neither done (the streak-at-risk
 * alert's territory) yields null.
 */

import type { Completion } from './streak';

export type MissingActivity = 'meditation' | 'affirmations';

export function missingDayGoalActivity(rows: Completion[], todayEpochDay: number): MissingActivity | null {
  const today = rows.find((row) => row.epochDay === todayEpochDay);
  if (!today) return null;
  if (today.meditationDone && !today.affirmationDone) return 'affirmations';
  if (today.affirmationDone && !today.meditationDone) return 'meditation';
  return null;
}

export const PREFERRED_TONES = ['soft', 'direct', 'powerful', 'manifestation'] as const;
export type PreferredTone = (typeof PREFERRED_TONES)[number];

/** Whitelists the synced `preferredTone` value; anything else (absent, wrong type, unknown) is no tone. */
export function parsePreferredTone(raw: unknown): PreferredTone | null {
  return typeof raw === 'string' && (PREFERRED_TONES as readonly string[]).includes(raw)
    ? (raw as PreferredTone)
    : null;
}
