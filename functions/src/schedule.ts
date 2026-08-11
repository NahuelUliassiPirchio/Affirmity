/**
 * Port of `NotificationSchedule` (app/src/main/java/com/pirxhio/affirmity/notifications/NotificationSchedule.kt)
 * -- pure slot-randomization math, no I/O. `nextTriggerAtMillis`'s "roll forward if now has
 * already passed the window" responsibility does not port 1:1: the planner always computes a
 * slot for one explicit `localDay` (never "the next occurrence after now"), so idempotent
 * per-day planning (see planner.ts / design.md) replaces that roll-forward branch.
 */

import { localInstantMillis } from './localDay';
import type { NotificationChannel, PlannedTask } from './planner';

/**
 * The four fixed, user-selectable day-part segments (minutes since local midnight). Mirrors the
 * client's `DaySegment` enum (app/src/main/java/com/pirxhio/affirmity/data/local/DaySegment.kt) --
 * the string keys here must match `DaySegment.key` exactly, since they round-trip through
 * Firestore's `${prefsPrefix}_segments` array field.
 */
export const DAY_SEGMENTS: Record<string, { startMinute: number; endMinute: number }> = {
  madrugada: { startMinute: 0, endMinute: 360 },
  manana: { startMinute: 360, endMinute: 720 },
  tarde: { startMinute: 720, endMinute: 1080 },
  noche: { startMinute: 1080, endMinute: 1440 },
};

/**
 * Distributes `slotCount` instants across the selected `segments`, assigning slot `i` to
 * `segments[i % segments.length]` (round-robin) so every selected segment gets at least one slot
 * whenever `slotCount >= segments.length`, then picks one random instant inside that segment via
 * `slotInstant`. No segments selected -> no tasks (a channel can be enabled with nothing picked;
 * that's a valid silent no-op, not an error). An unrecognized segment key is skipped rather than
 * thrown, since it should never happen from a trusted client but must not crash the planner pass
 * if it somehow does.
 */
export function segmentSlots(
  localDay: number,
  zone: string,
  segments: string[],
  slotCount: number,
  channel: NotificationChannel,
  rng: () => number = Math.random,
): PlannedTask[] {
  if (segments.length === 0) return [];
  const tasks: PlannedTask[] = [];
  for (let i = 0; i < slotCount; i++) {
    const range = DAY_SEGMENTS[segments[i % segments.length]];
    if (!range) continue;
    const instant = slotInstant(localDay, zone, range.startMinute, range.endMinute, rng);
    tasks.push({ uid: '', localDay, channel, slot: i, atMillis: instant.getTime() });
  }
  return tasks;
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
