import { describe, expect, it } from 'vitest';

import { streakOf, shouldFireStreakAlert, currentStreak, streakBand, type Completion } from '../src/streak';

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

  // Spec scenario: "Fires when streak is live and day incomplete".
  it('fires when streak is live and day incomplete', () => {
    const rows: Completion[] = [
      { epochDay: MONDAY - 2, meditationDone: true, affirmationDone: true },
      { epochDay: MONDAY - 1, meditationDone: true, affirmationDone: true },
      { epochDay: MONDAY, meditationDone: true, affirmationDone: false },
    ];

    expect(shouldFireStreakAlert(rows, MONDAY)).toBe(true);
  });

  // Spec scenario: "Does not fire once the day is fully completed".
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

describe('currentStreak', () => {
  it('takes the longer of the meditation/affirmation streaks', () => {
    const rows: Completion[] = [
      { epochDay: MONDAY - 2, meditationDone: true, affirmationDone: false },
      { epochDay: MONDAY - 1, meditationDone: true, affirmationDone: false },
      { epochDay: MONDAY, meditationDone: true, affirmationDone: true },
    ];

    expect(currentStreak(rows, MONDAY)).toBe(3);
  });

  it('is 0 when neither track has an active streak', () => {
    expect(currentStreak([], MONDAY)).toBe(0);
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
