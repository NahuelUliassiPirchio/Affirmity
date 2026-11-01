/**
 * IANA time-zone <-> epoch-day <-> UTC-instant helpers.
 *
 * `epochDay` is the local CALENDAR date's days since 1970-01-01 (Android client:
 * `LocalDate.of(y, m, d).toEpochDay()` in `DayClock.kt`). It is injective per calendar date in
 * every zone, DST included. Helpers that need an instant map the calendar date back to the UTC
 * instant of local midnight / local wall time in the zone.
 * No external date library is used -- the `Intl.DateTimeFormat` API (available in Node 20) is
 * sufficient and keeps `functions/` dependency-free for this pure layer.
 */

const MS_PER_DAY = 86_400_000;
const OFFSET_SAMPLE_RANGE_MS = 36 * 60 * 60_000;

interface ZonedParts {
  year: number;
  month: number; // 1-12
  day: number;
  hour: number;
  minute: number;
  second: number;
}

function getZonedParts(utcMillis: number, zone: string): ZonedParts {
  const formatter = new Intl.DateTimeFormat('en-US', {
    timeZone: zone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  });
  const map: Record<string, string> = {};
  for (const part of formatter.formatToParts(new Date(utcMillis))) {
    if (part.type !== 'literal') map[part.type] = part.value;
  }
  // Some locales/environments render midnight as "24" under hour12: false.
  const hour = map.hour === '24' ? 0 : Number(map.hour);
  return {
    year: Number(map.year),
    month: Number(map.month),
    day: Number(map.day),
    hour,
    minute: Number(map.minute),
    second: Number(map.second),
  };
}

function zonedPartsAsUtcMillis(parts: ZonedParts): number {
  return Date.UTC(parts.year, parts.month - 1, parts.day, parts.hour, parts.minute, parts.second);
}

function zoneOffsetMillis(utcMillis: number, zone: string): number {
  return zonedPartsAsUtcMillis(getZonedParts(utcMillis, zone)) - utcMillis;
}

/**
 * UTC millis for the given local wall-clock date/time in `zone`. Candidate instants are built
 * from the offsets surrounding the target date. Ambiguous fall-back times resolve to their
 * earlier occurrence; nonexistent spring-forward times advance by the gap to the corresponding
 * valid post-transition wall time (for example 02:30 -> 03:30).
 */
function zonedTimeToUtcMillis(
  year: number,
  month: number,
  day: number,
  hour: number,
  minute: number,
  second: number,
  zone: string,
): number {
  const target = Date.UTC(year, month - 1, day, hour, minute, second);
  const offsets = new Set([
    zoneOffsetMillis(target - OFFSET_SAMPLE_RANGE_MS, zone),
    zoneOffsetMillis(target, zone),
    zoneOffsetMillis(target + OFFSET_SAMPLE_RANGE_MS, zone),
  ]);
  const candidates = [...offsets].map((offset) => {
    const utcMillis = target - offset;
    return { utcMillis, wallMillis: zonedPartsAsUtcMillis(getZonedParts(utcMillis, zone)) };
  });

  const exact = candidates
    .filter((candidate) => candidate.wallMillis === target)
    .sort((left, right) => left.utcMillis - right.utcMillis);
  if (exact.length > 0) return exact[0].utcMillis;

  const advanced = candidates
    .filter((candidate) => candidate.wallMillis > target)
    .sort((left, right) => left.wallMillis - right.wallMillis || left.utcMillis - right.utcMillis);
  if (advanced.length > 0) return advanced[0].utcMillis;

  return candidates.sort((left, right) => right.wallMillis - left.wallMillis)[0].utcMillis;
}

function utcCalendarToYmd(utcDay: number): { year: number; month: number; day: number } {
  const d = new Date(utcDay * MS_PER_DAY);
  return { year: d.getUTCFullYear(), month: d.getUTCMonth() + 1, day: d.getUTCDate() };
}

/** UTC millis for local midnight of the calendar date `ymd` in `zone` (first valid instant if 00:00 is skipped). */
function midnightOfYmd(ymd: { year: number; month: number; day: number }, zone: string): number {
  return zonedTimeToUtcMillis(ymd.year, ymd.month, ymd.day, 0, 0, 0, zone);
}

/** UTC millis for local midnight (00:00:00) of `epochDay` in `zone`. */
export function localMidnightUtcMillis(epochDay: number, zone: string): number {
  return midnightOfYmd(utcCalendarToYmd(epochDay), zone);
}

/** UTC millis for `minuteOfDay` on `epochDay` in `zone`, resolved as a complete local date/time. */
export function localInstantMillis(epochDay: number, zone: string, minuteOfDay: number): number {
  const { year, month, day } = utcCalendarToYmd(epochDay);
  const hour = Math.floor(minuteOfDay / 60);
  const minute = minuteOfDay % 60;
  return zonedTimeToUtcMillis(year, month, day, hour, minute, 0, zone);
}

/** The local calendar date's epoch day (days since 1970-01-01) that `utcMillis` falls on in `zone`. */
export function utcMillisToLocalEpochDay(utcMillis: number, zone: string): number {
  const zoned = getZonedParts(utcMillis, zone);
  return Date.UTC(zoned.year, zoned.month - 1, zoned.day) / MS_PER_DAY;
}

/** The local hour-of-day (0-23) that `utcMillis` falls on when viewed in `zone`. */
export function localHourInZone(utcMillis: number, zone: string): number {
  return getZonedParts(utcMillis, zone).hour;
}

/** The local minute-of-day (0-1439) that `utcMillis` falls on when viewed in `zone`. */
export function localMinuteOfDay(utcMillis: number, zone: string): number {
  const zoned = getZonedParts(utcMillis, zone);
  return zoned.hour * 60 + zoned.minute;
}
