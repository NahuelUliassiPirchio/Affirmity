import { describe, expect, it } from 'vitest';

import { DAY_SEGMENTS, segmentSlots, slotInstant } from '../src/schedule';

// Deterministic LCG so `slotInstant`'s rng argument is reproducible in tests -- mirrors the
// seeded `kotlin.random.Random(seed)` used in the retired NotificationScheduleTest.
function seededRng(seed: number): () => number {
  let state = seed % 2147483647;
  if (state <= 0) state += 2147483646;
  return () => {
    state = (state * 16807) % 2147483647;
    return (state - 1) / 2147483646;
  };
}

const ZONE = 'UTC';
const TEST_LOCAL_DAY = 19000;

function minuteOfDayUtc(date: Date): number {
  return date.getUTCHours() * 60 + date.getUTCMinutes();
}

describe('segmentSlots', () => {
  it('puts every instant inside the single selected segment', () => {
    const tasks = segmentSlots(TEST_LOCAL_DAY, ZONE, ['manana'], 3, 'reminder', seededRng(1));

    expect(tasks).toHaveLength(3);
    const { startMinute, endMinute } = DAY_SEGMENTS.manana;
    for (const task of tasks) {
      const minute = minuteOfDayUtc(new Date(task.atMillis));
      expect(minute).toBeGreaterThanOrEqual(startMinute);
      expect(minute).toBeLessThanOrEqual(endMinute);
    }
  });

  it('round-robins slots across multiple selected segments', () => {
    const tasks = segmentSlots(TEST_LOCAL_DAY, ZONE, ['manana', 'noche'], 3, 'reminder', seededRng(2));

    expect(tasks).toHaveLength(3);
    const bounds = [DAY_SEGMENTS.manana, DAY_SEGMENTS.noche, DAY_SEGMENTS.manana];
    tasks.forEach((task, i) => {
      const minute = minuteOfDayUtc(new Date(task.atMillis));
      expect(minute).toBeGreaterThanOrEqual(bounds[i].startMinute);
      expect(minute).toBeLessThanOrEqual(bounds[i].endMinute);
    });
  });

  it('returns no tasks when no segment is selected', () => {
    expect(segmentSlots(TEST_LOCAL_DAY, ZONE, [], 3, 'reminder', seededRng(3))).toEqual([]);
  });

  it('skips an unrecognized segment key instead of throwing', () => {
    const tasks = segmentSlots(TEST_LOCAL_DAY, ZONE, ['not-a-segment'], 1, 'reminder', seededRng(4));

    expect(tasks).toEqual([]);
  });
});

describe('slotInstant', () => {
  // Ported from `pick falls within configured window on the same day`.
  it('pick falls within the configured window', () => {
    const startMinute = 9 * 60;
    const endMinute = 21 * 60;

    const result = slotInstant(TEST_LOCAL_DAY, ZONE, startMinute, endMinute, seededRng(1));

    const minute = minuteOfDayUtc(result);
    expect(minute).toBeGreaterThanOrEqual(startMinute);
    expect(minute).toBeLessThanOrEqual(endMinute);
  });

  // Ported from `degenerate window with equal start and end always picks that exact minute`.
  it('degenerate window with equal start and end always picks that exact minute', () => {
    const fixedMinute = 12 * 60;

    const result = slotInstant(TEST_LOCAL_DAY, ZONE, fixedMinute, fixedMinute, seededRng(3));

    expect(minuteOfDayUtc(result)).toBe(fixedMinute);
  });

  // Ported from `inverted window is clamped to a zero-length span at start`.
  it('inverted window is clamped to a zero-length span at start', () => {
    const startMinute = 21 * 60;
    const endMinute = 9 * 60; // inverted

    const result = slotInstant(TEST_LOCAL_DAY, ZONE, startMinute, endMinute, seededRng(4));

    expect(minuteOfDayUtc(result)).toBe(startMinute);
  });

  // Substitutes for `rolls to next day when today's window has already passed` /
  // `late fire after window end still rolls to next day's window`: the planner always targets one
  // explicit `localDay` rather than "the next occurrence after now", so there is no roll-forward
  // branch to port -- this instead asserts the computed instant lands on the exact requested day.
  it('lands on the exact requested local day (no roll-forward -- the planner supplies an explicit target day)', () => {
    const result = slotInstant(TEST_LOCAL_DAY, ZONE, 9 * 60, 21 * 60, seededRng(2));

    const epochDay = Math.floor(result.getTime() / 86_400_000);
    expect(epochDay).toBe(TEST_LOCAL_DAY);
  });

  it('is reproducible for a given seeded rng sequence', () => {
    const first = slotInstant(TEST_LOCAL_DAY, ZONE, 9 * 60, 21 * 60, seededRng(7));
    const second = slotInstant(TEST_LOCAL_DAY, ZONE, 9 * 60, 21 * 60, seededRng(7));

    expect(first.getTime()).toBe(second.getTime());
  });

  it('respects the local time zone offset, not just UTC', () => {
    const fixedMinute = 12 * 60;

    const utcResult = slotInstant(TEST_LOCAL_DAY, 'UTC', fixedMinute, fixedMinute, seededRng(5));
    const nyResult = slotInstant(TEST_LOCAL_DAY, 'America/New_York', fixedMinute, fixedMinute, seededRng(5));

    expect(nyResult.getTime()).not.toBe(utcResult.getTime());
  });
});
