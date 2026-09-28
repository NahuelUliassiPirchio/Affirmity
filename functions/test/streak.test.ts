import { describe, expect, it } from 'vitest';

import {
  streakOf,
  shouldFireStreakAlert,
  currentStreak,
  streakBand,
  selectStreakAlert,
  ACTIVITY_STREAK_MIN_DAYS,
  type Completion,
} from '../src/streak';

const MONDAY = 100;

describe('streakOf', () => {
  // Ported from `streak resets to zero the day after a missed day`.
  it('resets to zero the day after a missed day', () => {
    const rows: Completion[] = [
      { epochDay: MONDAY, meditationDone: true, affirmationDone: true },
      // Tuesday (MONDAY + 1) missing entirely.
      { epochDay: MONDAY + 2, meditationDone: true, affirmationDone: true },
    ];

    expect(streakOf(rows, MONDAY + 1, (row) => row.meditationDone)).toBe(0);
  });

  // Ported from `streak counts contiguous completed days ending today`.
  it('counts contiguous completed days ending today', () => {
    const rows: Completion[] = [
      { epochDay: MONDAY, meditationDone: true, affirmationDone: true },
      { epochDay: MONDAY + 1, meditationDone: true, affirmationDone: true },
      { epochDay: MONDAY + 2, meditationDone: true, affirmationDone: true },
    ];

    expect(streakOf(rows, MONDAY + 2, (row) => row.meditationDone)).toBe(3);
  });

  // Ported from `affirmationDone and meditationDone are tracked independently for the same day`.
  it('tracks affirmationDone and meditationDone independently for the same day', () => {
    const rows: Completion[] = [{ epochDay: MONDAY, meditationDone: true, affirmationDone: false }];

    expect(streakOf(rows, MONDAY, (row) => row.meditationDone)).toBe(1);
    expect(streakOf(rows, MONDAY, (row) => row.affirmationDone)).toBe(0);
  });

  it('a day with no matching row counts as not done', () => {
    expect(streakOf([], MONDAY, (row) => row.meditationDone)).toBe(0);
  });
});

describe('shouldFireStreakAlert', () => {
  it('fires when a streak is live through yesterday and today has no completion yet', () => {
    const rows: Completion[] = [
      { epochDay: MONDAY - 2, meditationDone: true, affirmationDone: true },
      { epochDay: MONDAY - 1, meditationDone: true, affirmationDone: true },
    ];

    expect(shouldFireStreakAlert(rows, MONDAY)).toBe(true);
  });

  // General streak: one activity today is enough to keep it alive.
  it('does not fire once any activity is done today', () => {
    const rows: Completion[] = [
      { epochDay: MONDAY - 2, meditationDone: true, affirmationDone: true },
      { epochDay: MONDAY - 1, meditationDone: true, affirmationDone: true },
      { epochDay: MONDAY, meditationDone: true, affirmationDone: false },
    ];

    expect(shouldFireStreakAlert(rows, MONDAY)).toBe(false);
  });

  it('fires when the streak was held by a mix of activities and today has none', () => {
    const rows: Completion[] = [
      { epochDay: MONDAY - 2, meditationDone: true, affirmationDone: false },
      { epochDay: MONDAY - 1, meditationDone: false, affirmationDone: true },
      { epochDay: MONDAY, meditationDone: false, affirmationDone: false },
    ];

    expect(shouldFireStreakAlert(rows, MONDAY)).toBe(true);
  });

  it('does not fire once the day is fully completed', () => {
    const rows: Completion[] = [
      { epochDay: MONDAY - 1, meditationDone: true, affirmationDone: true },
      { epochDay: MONDAY, meditationDone: true, affirmationDone: true },
    ];

    expect(shouldFireStreakAlert(rows, MONDAY)).toBe(false);
  });

  // Spec scenario: "Does not fire with no active streak".
  it('does not fire with no active streak', () => {
    const rows: Completion[] = [{ epochDay: MONDAY, meditationDone: false, affirmationDone: false }];

    expect(shouldFireStreakAlert(rows, MONDAY)).toBe(false);
  });
});

describe('currentStreak (general streak)', () => {
  it('counts consecutive days with at least one activity, mixing meditation and affirmations', () => {
    const rows: Completion[] = [
      { epochDay: MONDAY - 2, meditationDone: true, affirmationDone: false },
      { epochDay: MONDAY - 1, meditationDone: false, affirmationDone: true },
      { epochDay: MONDAY, meditationDone: true, affirmationDone: true },
    ];

    expect(currentStreak(rows, MONDAY)).toBe(3);
  });

  it('stops at a day with no activity even if the other days are long streaks', () => {
    const rows: Completion[] = [
      { epochDay: MONDAY - 3, meditationDone: true, affirmationDone: false },
      { epochDay: MONDAY - 2, meditationDone: false, affirmationDone: false },
      { epochDay: MONDAY - 1, meditationDone: false, affirmationDone: true },
    ];

    expect(currentStreak(rows, MONDAY - 1)).toBe(1);
  });

  it('is 0 when there is no activity', () => {
    expect(currentStreak([], MONDAY)).toBe(0);
  });
});

describe('selectStreakAlert', () => {
  const med = (day: number): Completion => ({ epochDay: day, meditationDone: true, affirmationDone: false });
  const aff = (day: number): Completion => ({ epochDay: day, meditationDone: false, affirmationDone: true });
  const both = (day: number): Completion => ({ epochDay: day, meditationDone: true, affirmationDone: true });

  it('exposes the per-activity threshold as 3 days', () => {
    expect(ACTIVITY_STREAK_MIN_DAYS).toBe(3);
  });

  it('returns the general streak when no single activity has a live streak of 3+', () => {
    const rows = [med(MONDAY - 3), aff(MONDAY - 2), med(MONDAY - 1)];

    expect(selectStreakAlert(rows, MONDAY)).toEqual({ streakCount: 3 });
  });

  it('returns a meditation-specific alert when only meditation has a live streak of 3+ through yesterday', () => {
    const rows = [med(MONDAY - 5), med(MONDAY - 4), med(MONDAY - 3), med(MONDAY - 2), med(MONDAY - 1)];

    expect(selectStreakAlert(rows, MONDAY)).toEqual({ streakCount: 5, activity: 'meditation' });
  });

  it('returns an affirmations-specific alert when only affirmations has a live streak of 3+', () => {
    const rows = [aff(MONDAY - 3), aff(MONDAY - 2), aff(MONDAY - 1)];

    expect(selectStreakAlert(rows, MONDAY)).toEqual({ streakCount: 3, activity: 'affirmations' });
  });

  it('uses the activity streak count, not the general one, for an activity alert', () => {
    // General streak is 4 (aff on -4, then med x3); meditation alone is 3.
    const rows = [aff(MONDAY - 4), med(MONDAY - 3), med(MONDAY - 2), med(MONDAY - 1)];

    expect(selectStreakAlert(rows, MONDAY)).toEqual({ streakCount: 3, activity: 'meditation' });
  });

  it('falls back to the general alert when both activities have live streaks of 3+', () => {
    const rows = [both(MONDAY - 3), both(MONDAY - 2), both(MONDAY - 1)];

    expect(selectStreakAlert(rows, MONDAY)).toEqual({ streakCount: 3 });
  });

  it('keeps the general alert when the activity streak is below the threshold', () => {
    const rows = [med(MONDAY - 2), med(MONDAY - 1)];

    expect(selectStreakAlert(rows, MONDAY)).toEqual({ streakCount: 2 });
  });

  it('returns null when there is no live general streak through yesterday', () => {
    expect(selectStreakAlert([med(MONDAY - 3)], MONDAY)).toBeNull();
  });
});

describe('shouldFireStreakAlert and selectStreakAlert stay consistent', () => {
  it('whenever the alert should fire, the selection is non-null (exhaustive over 5 days x 4 states)', () => {
    const states: Array<[boolean, boolean]> = [[false, false], [true, false], [false, true], [true, true]];
    const days = [0, 1, 2, 3, 4];
    const total = states.length ** days.length;
    for (let mask = 0; mask < total; mask++) {
      const rows: Completion[] = days.map((offset, index) => {
        const [meditationDone, affirmationDone] = states[Math.floor(mask / states.length ** index) % states.length];
        return { epochDay: MONDAY - 5 + offset, meditationDone, affirmationDone };
      });
      if (shouldFireStreakAlert(rows, MONDAY)) {
        expect(selectStreakAlert(rows, MONDAY)).not.toBeNull();
      }
    }
  });
});

describe('streakBand', () => {
  // design §1/File Changes: streakBand(count): 'streak_1_3'|'streak_4_13'|'streak_14plus'
  it('classifies the low end of the 1-3 band', () => {
    expect(streakBand(1)).toBe('streak_1_3');
  });

  it('classifies the high boundary of the 1-3 band', () => {
    expect(streakBand(3)).toBe('streak_1_3');
  });

  it('classifies the low boundary of the 4-13 band', () => {
    expect(streakBand(4)).toBe('streak_4_13');
  });

  it('classifies the high boundary of the 4-13 band', () => {
    expect(streakBand(13)).toBe('streak_4_13');
  });

  it('classifies the low boundary of the 14+ band', () => {
    expect(streakBand(14)).toBe('streak_14plus');
  });

  it('classifies a large streak into the 14+ band', () => {
    expect(streakBand(500)).toBe('streak_14plus');
  });
});
