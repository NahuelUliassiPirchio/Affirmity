/**
 * Port of `NotificationSchedule` (app/src/main/java/com/pirxhio/affirmity/notifications/NotificationSchedule.kt)
 * -- pure slot-randomization math, no I/O. `nextTriggerAtMillis`'s "roll forward if now has
 * already passed the window" responsibility does not port 1:1: the planner always computes a
 * slot for one explicit `localDay` (never "the next occurrence after now"), so idempotent
 * per-day planning (see planner.ts / design.md) replaces that roll-forward branch.
 */

import { localInstantMillis } from './localDay';

/**
 * Splits `[startMinute, endMinute]` into `slotCount` equal-length sub-windows and returns the
 * `[start, end]` bounds of `slotIndex`'s share -- the last slot absorbs any remainder minute so
 * the slots always cover the full window with no gap or overlap.
 */
export function subWindow(
  startMinute: number,
  endMinute: number,
  slotIndex: number,
  slotCount: number,
): [number, number] {
  if (slotCount <= 0) {
    throw new Error('slotCount must be positive');
  }
  if (slotIndex < 0 || slotIndex >= slotCount) {
    throw new Error(`slotIndex (${slotIndex}) out of range for slotCount (${slotCount})`);
  }
  const span = Math.max(endMinute - startMinute, 0);
  const slotSpan = Math.floor(span / slotCount);
  const slotStart = startMinute + slotSpan * slotIndex;
  const slotEnd = slotIndex === slotCount - 1 ? startMinute + span : slotStart + slotSpan;
  return [slotStart, slotEnd];
}

/**
 * A random instant inside `[startMinute, endMinute]` (minutes since local midnight) on the given
 * `localDay`, in `zone`. `rng` must return a value in `[0, 1)` (defaults to `Math.random`); pass a
 * seeded generator for deterministic tests.
 */
export function slotInstant(
  localDay: number,
  zone: string,
  startMinute: number,
  endMinute: number,
  rng: () => number = Math.random,
): Date {
  const span = Math.max(endMinute - startMinute, 0);
  const offset = Math.min(Math.floor(rng() * (span + 1)), span);
  const minuteOfDay = startMinute + offset;
  return new Date(localInstantMillis(localDay, zone, minuteOfDay));
}
