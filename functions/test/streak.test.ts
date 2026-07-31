import { describe, expect, it } from 'vitest';

import { streakOf, shouldFireStreakAlert, type Completion } from '../src/streak';

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
