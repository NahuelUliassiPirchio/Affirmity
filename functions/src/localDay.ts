/**
 * IANA time-zone <-> epoch-day <-> UTC-instant helpers.
 *
 * `epochDay` is days since 1970-01-01, aligned to the UTC calendar (same convention as the
 * retired client's `LocalDate.toEpochDay()` / `DayClock.epochDay`). No external date library is
 * used -- the `Intl.DateTimeFormat` API (available in Node 20) is sufficient and keeps
 * `functions/` dependency-free for this pure layer.
 */

const MS_PER_MINUTE = 60_000;
const MS_PER_DAY = 86_400_000;

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

/**
 * UTC millis for the given local wall-clock date/time in `zone`. Uses the standard
 * guess-then-correct technique: format a first guess back through the zone and adjust for the
 * observed offset (handles DST transitions with one extra correction pass).
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
  let guess = target;
  for (let i = 0; i < 2; i++) {
    const zoned = getZonedParts(guess, zone);
    const zonedAsUtc = Date.UTC(zoned.year, zoned.month - 1, zoned.day, zoned.hour, zoned.minute, zoned.second);
    const diff = zonedAsUtc - target;
    if (diff === 0) break;
    guess -= diff;
  }
  return guess;
}

/** epoch day -> {year, month, day} (UTC-calendar-aligned, matches `epochDay * MS_PER_DAY`). */
function epochDayToYmd(epochDay: number): { year: number; month: number; day: number } {
  const d = new Date(epochDay * MS_PER_DAY);
  return { year: d.getUTCFullYear(), month: d.getUTCMonth() + 1, day: d.getUTCDate() };
}

/** UTC millis for local midnight (00:00:00) of `epochDay` in `zone`. */
export function localMidnightUtcMillis(epochDay: number, zone: string): number {
  const { year, month, day } = epochDayToYmd(epochDay);
  return zonedTimeToUtcMillis(year, month, day, 0, 0, 0, zone);
}

/** UTC millis for local midnight of `epochDay` in `zone`, plus `minuteOfDay` minutes. */
export function localInstantMillis(epochDay: number, zone: string, minuteOfDay: number): number {
  return localMidnightUtcMillis(epochDay, zone) + minuteOfDay * MS_PER_MINUTE;
}

/** The epoch day (UTC-calendar-aligned) that `utcMillis` falls on when viewed in `zone`. */
export function utcMillisToLocalEpochDay(utcMillis: number, zone: string): number {
  const zoned = getZonedParts(utcMillis, zone);
  return Math.floor(Date.UTC(zoned.year, zoned.month - 1, zoned.day) / MS_PER_DAY);
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
