import { describe, expect, it } from 'vitest';

import { localInstantMillis, localMinuteOfDay, utcMillisToLocalEpochDay } from '../src/localDay';

const MS_PER_DAY = 86_400_000;

function epochDay(year: number, month: number, day: number): number {
  return Math.floor(Date.UTC(year, month - 1, day) / MS_PER_DAY);
}

describe('localInstantMillis', () => {
  it('advances a nonexistent spring-gap wall time by the DST gap', () => {
    const localDay = epochDay(2024, 3, 10);

    const result = localInstantMillis(localDay, 'America/New_York', 2 * 60 + 30);

    expect(new Date(result).toISOString()).toBe('2024-03-10T07:30:00.000Z');
    expect(localMinuteOfDay(result, 'America/New_York')).toBe(3 * 60 + 30);
    expect(utcMillisToLocalEpochDay(result, 'America/New_York')).toBe(localDay);
  });

  it('uses the earlier occurrence of an ambiguous autumn wall time', () => {
    const localDay = epochDay(2024, 11, 3);

    const result = localInstantMillis(localDay, 'America/New_York', 1 * 60 + 30);

    expect(new Date(result).toISOString()).toBe('2024-11-03T05:30:00.000Z');
    expect(localMinuteOfDay(result, 'America/New_York')).toBe(1 * 60 + 30);
    expect(utcMillisToLocalEpochDay(result, 'America/New_York')).toBe(localDay);
  });

  it('keeps the requested wall-clock time after the spring DST transition', () => {
    const localDay = epochDay(2024, 3, 10);

    const result = localInstantMillis(localDay, 'America/New_York', 20 * 60);

    expect(new Date(result).toISOString()).toBe('2024-03-11T00:00:00.000Z');
    expect(localMinuteOfDay(result, 'America/New_York')).toBe(20 * 60);
    expect(utcMillisToLocalEpochDay(result, 'America/New_York')).toBe(localDay);
  });

  it('keeps the requested wall-clock time after the autumn DST transition', () => {
    const localDay = epochDay(2024, 11, 3);

    const result = localInstantMillis(localDay, 'America/New_York', 20 * 60);

    expect(new Date(result).toISOString()).toBe('2024-11-04T01:00:00.000Z');
    expect(localMinuteOfDay(result, 'America/New_York')).toBe(20 * 60);
    expect(utcMillisToLocalEpochDay(result, 'America/New_York')).toBe(localDay);
  });
});
