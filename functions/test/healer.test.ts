import { describe, expect, it } from 'vitest';

import {
  shouldFireHealerAlert,
  isHealerExpiringToday,
  HEALER_EPOCH_START_DAY,
  type HealerUse,
} from '../src/healer';
import type { Completion } from '../src/streak';

// Always well after the rollout floor, mirroring StreakHealerStatsTest.kt's `start` anchor.
const start = HEALER_EPOCH_START_DAY + 10;

function fullDay(epochDay: number): Completion {
  return { epochDay, meditationDone: true, affirmationDone: true };
}

describe('shouldFireHealerAlert', () => {
  it('fires the day after a break when a healer was held', () => {
    const rows = [fullDay(start), fullDay(start + 1)];
    // day start + 2 has no row: zero activity, the break.

    expect(shouldFireHealerAlert(rows, [], start + 3)).toBe(true);
  });

  it('does not fire on the break day itself, only the day after', () => {
    const rows = [fullDay(start), fullDay(start + 1)];

    expect(shouldFireHealerAlert(rows, [], start + 2)).toBe(false);
  });

  it('does not fire once the break day was already healed', () => {
    const rows = [fullDay(start), fullDay(start + 1)];
    const uses: HealerUse[] = [{ healedEpochDay: start + 2 }];

    expect(shouldFireHealerAlert(rows, uses, start + 3)).toBe(false);
  });

  it('does not fire without a held healer (only one full day, no grant)', () => {
    const rows = [fullDay(start)];
    // day start + 1 has no row: zero activity, the break.

    expect(shouldFireHealerAlert(rows, [], start + 2)).toBe(false);
  });

  it('does not fire when the streak never broke', () => {
    const rows = [fullDay(start), fullDay(start + 1), fullDay(start + 2)];

    expect(shouldFireHealerAlert(rows, [], start + 2)).toBe(false);
  });

  it('never grants from completions before the rollout floor', () => {
    // Two full days entirely before HEALER_EPOCH_START_DAY, then a break right at the floor.
    const rows = [fullDay(HEALER_EPOCH_START_DAY - 2), fullDay(HEALER_EPOCH_START_DAY - 1)];

    expect(shouldFireHealerAlert(rows, [], HEALER_EPOCH_START_DAY + 1)).toBe(false);
  });
});

describe('isHealerExpiringToday', () => {
  // design §5: the healer's eligibility window is exactly one day (breakDay + 1) -- if it is
  // available today, tomorrow's window can never contain this same break day, so it is always
  // "expiring today" whenever it is available at all.
  it('is true on the single day the healer is available (the edge of tomorrow\'s window)', () => {
    const rows = [fullDay(start), fullDay(start + 1)];
    // day start + 2 has no row: zero activity, the break.

    expect(isHealerExpiringToday(rows, [], start + 3)).toBe(true);
  });

  it('is false on the break day itself, before the window opens', () => {
    const rows = [fullDay(start), fullDay(start + 1)];

    expect(isHealerExpiringToday(rows, [], start + 2)).toBe(false);
  });

  it('is false the day after the single-day window has already closed', () => {
    const rows = [fullDay(start), fullDay(start + 1)];
    // Healer was available at start + 3 (tested above); one more day out, it is no longer eligible.

    expect(isHealerExpiringToday(rows, [], start + 4)).toBe(false);
  });

  it('is false once the break day was already healed', () => {
    const rows = [fullDay(start), fullDay(start + 1)];
    const uses: HealerUse[] = [{ healedEpochDay: start + 2 }];

    expect(isHealerExpiringToday(rows, uses, start + 3)).toBe(false);
  });
});
