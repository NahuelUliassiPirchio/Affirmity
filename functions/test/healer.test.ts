import { describe, expect, it } from 'vitest';

import {
  deriveHealerInventory,
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

describe('deriveHealerInventory', () => {
  it('awards balances zero one one two for four complete days', () => {
    const rows = [0, 1, 2, 3].map((offset) => fullDay(start + offset));
    expect([0, 1, 2, 3].map((offset) => deriveHealerInventory(rows, [], start + offset).healerCount))
      .toEqual([0, 1, 1, 2]);
    expect([0, 1, 2, 3].map((offset) => deriveHealerInventory(rows, [], start + offset).pairProgress))
      .toEqual([1, 0, 1, 0]);
  });
});

describe('inventory replay parity', () => {
  it.each([
    [[{ epochDay: start + 1, meditationDone: true, affirmationDone: false }], []],
    [[{ epochDay: start + 1, meditationDone: false, affirmationDone: true }], []],
    [[], []],
    [[], [{ healedEpochDay: start + 1 }]],
  ])('interruptions reset a pair without earning', (middle, uses) => {
    expect(deriveHealerInventory([fullDay(start), ...middle, fullDay(start + 2)], uses, start + 2))
      .toEqual({ healerCount: 0, pairProgress: 1 });
  });

  it('does not bank credit at capacity and refills from a fresh activation-day pair', () => {
    const rows = Array.from({ length: 9 }, (_, offset) => fullDay(start + offset));
    const uses = [{ healedEpochDay: start + 9 }];
    expect(deriveHealerInventory(rows, [], start + 8)).toEqual({ healerCount: 2, pairProgress: 0 });
    expect(deriveHealerInventory(rows, uses, start + 10)).toEqual({ healerCount: 1, pairProgress: 0 });
    const completedFirst = [...rows, fullDay(start + 10)];
    expect(deriveHealerInventory(completedFirst, [], start + 10)).toEqual({ healerCount: 2, pairProgress: 0 });
    expect(deriveHealerInventory(completedFirst, uses, start + 10)).toEqual({ healerCount: 1, pairProgress: 1 });
    expect(deriveHealerInventory([...completedFirst, fullDay(start + 11)], uses, start + 11))
      .toEqual({ healerCount: 2, pairProgress: 0 });
  });

  it('spends one per successive missed day and deduplicates uses', () => {
    const rows = [0, 1, 2, 3].map((offset) => fullDay(start + offset));
    const first = [{ healedEpochDay: start + 4 }, { healedEpochDay: start + 4 }];
    expect(deriveHealerInventory(rows, first, start + 5)).toEqual({ healerCount: 1, pairProgress: 0 });
    expect(shouldFireHealerAlert(rows, first, start + 5)).toBe(false);
    expect(shouldFireHealerAlert(rows, first, start + 6)).toBe(true);
    expect(isHealerExpiringToday(rows, first, start + 6)).toBe(true);
    const second = [...first, { healedEpochDay: start + 5 }];
    expect(deriveHealerInventory(rows, second, start + 6)).toEqual({ healerCount: 0, pairProgress: 0 });
    expect(shouldFireHealerAlert(rows, second, start + 6)).toBe(false);
  });

  it('preserves orphan and conflicting historical healing without debt or earning', () => {
    const rows = [0, 1, 2].map((offset) => fullDay(start + offset));
    const uses = [{ healedEpochDay: start }, { healedEpochDay: start + 1 }, { healedEpochDay: start + 1 }];
    expect(deriveHealerInventory(rows, uses, start + 2)).toEqual({ healerCount: 0, pairProgress: 1 });
  });

  it('uses inclusive lookback and rollout bounds', () => {
    const today = HEALER_EPOCH_START_DAY + 400;
    const floor = today - 370;
    const rows = [-2, -1, 0, 1, 2, 3].map((offset) => fullDay(floor + offset));
    expect(deriveHealerInventory(rows, [], today)).toEqual({ healerCount: 2, pairProgress: 0 });
    expect(deriveHealerInventory([fullDay(floor - 1), fullDay(floor)], [], today).healerCount).toBe(0);
    expect(deriveHealerInventory([0, 1, 2, 3].map((offset) => fullDay(HEALER_EPOCH_START_DAY + offset)), [], HEALER_EPOCH_START_DAY + 3).healerCount).toBe(2);
  });

  it('declining retains both healers after expiry and an active yesterday is ineligible', () => {
    const rows = [0, 1, 2, 3].map((offset) => fullDay(start + offset));
    expect(shouldFireHealerAlert(rows, [], start + 5)).toBe(true);
    expect(shouldFireHealerAlert(rows, [], start + 6)).toBe(false);
    expect(deriveHealerInventory(rows, [], start + 6).healerCount).toBe(2);
    expect(shouldFireHealerAlert([...rows, fullDay(start + 5)], [], start + 6)).toBe(false);
  });
});
