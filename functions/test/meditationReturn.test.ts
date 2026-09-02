import { describe, expect, it } from 'vitest';

import {
  daysSinceLastMeditation,
  meditationReturnBand,
  shouldFireMeditationReturn,
  MEDITATION_RETURN_COOLDOWN_DAYS,
  MEDITATION_RETURN_LOOKBACK_DAYS,
  type MeditationReturnState,
} from '../src/meditationReturn';
import type { Completion } from '../src/streak';

const TODAY = 20000;

function meditated(epochDay: number): Completion {
  return { epochDay, meditationDone: true, affirmationDone: false };
}

const EMPTY_STATE: MeditationReturnState = {
  absenceStartLocalDay: null,
  lastSentLocalDay: null,
  lastBand: null,
};

describe('daysSinceLastMeditation', () => {
  it('returns null when there is no completion history at all', () => {
    expect(daysSinceLastMeditation([], TODAY)).toBeNull();
  });

  it('returns null when the user meditated today', () => {
    const rows = [meditated(TODAY)];

    expect(daysSinceLastMeditation(rows, TODAY)).toBeNull();
  });

  it('counts exactly 3 full days when the last meditation was 3 full days before yesterday', () => {
    // Last meditated TODAY - 4; yesterday is TODAY - 1; full inactive days = (TODAY-1) - (TODAY-4) = 3.
    const rows = [meditated(TODAY - 4)];

    expect(daysSinceLastMeditation(rows, TODAY)).toBe(3);
  });

  it('counts exactly 4 full days', () => {
    const rows = [meditated(TODAY - 5)];

    expect(daysSinceLastMeditation(rows, TODAY)).toBe(4);
  });

  it('counts exactly 7 full days', () => {
    const rows = [meditated(TODAY - 8)];

    expect(daysSinceLastMeditation(rows, TODAY)).toBe(7);
  });

  it('counts exactly 10 full days', () => {
    const rows = [meditated(TODAY - 11)];

    expect(daysSinceLastMeditation(rows, TODAY)).toBe(10);
  });

  it('returns null when the last meditation is beyond the lookback window', () => {
    const lastMeditatedDay = TODAY - 1 - (MEDITATION_RETURN_LOOKBACK_DAYS + 1);
    const rows = [meditated(lastMeditatedDay)];

    expect(daysSinceLastMeditation(rows, TODAY)).toBeNull();
  });

  it('includes a last-meditated day exactly at the lookback boundary', () => {
    const lastMeditatedDay = TODAY - MEDITATION_RETURN_LOOKBACK_DAYS;
    const rows = [meditated(lastMeditatedDay)];

    expect(daysSinceLastMeditation(rows, TODAY)).toBe(MEDITATION_RETURN_LOOKBACK_DAYS - 1);
  });

  it('ignores completion rows where meditationDone is false', () => {
    const rows = [{ epochDay: TODAY - 1, meditationDone: false, affirmationDone: true }, meditated(TODAY - 5)];

    expect(daysSinceLastMeditation(rows, TODAY)).toBe(4);
  });
});

describe('meditationReturnBand', () => {
  it.each([
    [2, null],
    [3, 'inactive_3_4'],
    [4, 'inactive_3_4'],
    [5, null],
    [7, 'inactive_7_10'],
    [10, 'inactive_7_10'],
    [11, null],
  ] as const)('maps %i inactive days to %s', (inactiveDays, expected) => {
    expect(meditationReturnBand(inactiveDays)).toBe(expected);
  });
});

describe('shouldFireMeditationReturn', () => {
  it('does not fire when the user meditated today', () => {
    const rows = [meditated(TODAY)];

    const result = shouldFireMeditationReturn(rows, TODAY, EMPTY_STATE);

    expect(result.fire).toBe(false);
  });

  it('does not fire when inactivity does not land in any band', () => {
    const rows = [meditated(TODAY - 6)]; // 5 full days inactive -- no band

    const result = shouldFireMeditationReturn(rows, TODAY, EMPTY_STATE);

    expect(result.fire).toBe(false);
  });

  it('fires the first time a band is reached with no prior state', () => {
    const rows = [meditated(TODAY - 4)]; // 3 full days inactive -- inactive_3_4

    const result = shouldFireMeditationReturn(rows, TODAY, EMPTY_STATE);

    expect(result).toEqual({
      fire: true,
      band: 'inactive_3_4',
      inactiveDays: 3,
      nextState: {
        absenceStartLocalDay: TODAY - 3,
        lastSentLocalDay: TODAY,
        lastBand: 'inactive_3_4',
      },
    });
  });

  it('does not re-fire the same band within the same absence', () => {
    const rows = [meditated(TODAY - 5)]; // 4 full days inactive -- still inactive_3_4
    const absenceStart = TODAY - 4;
    const state: MeditationReturnState = {
      absenceStartLocalDay: absenceStart,
      lastSentLocalDay: TODAY - 1,
      lastBand: 'inactive_3_4',
    };

    const result = shouldFireMeditationReturn(rows, TODAY, state);

    expect(result.fire).toBe(false);
  });

  it('resets stale state describing a different (earlier, already-resolved) absence', () => {
    // Stored state describes an absence that started long before the current one -- e.g. the user
    // meditated in between, resetting the streak of inactivity, so the old state must be ignored.
    const rows = [meditated(TODAY - 4)]; // current absence started at TODAY - 3
    const staleState: MeditationReturnState = {
      absenceStartLocalDay: TODAY - 100,
      lastSentLocalDay: TODAY - 90,
      lastBand: 'inactive_3_4',
    };

    const result = shouldFireMeditationReturn(rows, TODAY, staleState);

    expect(result.fire).toBe(true);
    expect(result.band).toBe('inactive_3_4');
  });

  it('blocks a second send within the cooldown window even for a new band', () => {
    const rows = [meditated(TODAY - 8)]; // 7 full days inactive -- inactive_7_10
    const absenceStart = TODAY - 7;
    const state: MeditationReturnState = {
      absenceStartLocalDay: absenceStart,
      lastSentLocalDay: TODAY - (MEDITATION_RETURN_COOLDOWN_DAYS - 1),
      lastBand: 'inactive_3_4',
    };

    const result = shouldFireMeditationReturn(rows, TODAY, state);

    expect(result.fire).toBe(false);
  });

  it('allows a new-band send once the cooldown window has fully elapsed', () => {
    const rows = [meditated(TODAY - 8)]; // 7 full days inactive -- inactive_7_10
    const absenceStart = TODAY - 7;
    const state: MeditationReturnState = {
      absenceStartLocalDay: absenceStart,
      lastSentLocalDay: TODAY - MEDITATION_RETURN_COOLDOWN_DAYS,
      lastBand: 'inactive_3_4',
    };

    const result = shouldFireMeditationReturn(rows, TODAY, state);

    expect(result.fire).toBe(true);
    expect(result.band).toBe('inactive_7_10');
    expect(result.nextState).toEqual({
      absenceStartLocalDay: absenceStart,
      lastSentLocalDay: TODAY,
      lastBand: 'inactive_7_10',
    });
  });
});
