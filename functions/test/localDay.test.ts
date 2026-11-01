import { describe, expect, it } from 'vitest';

import { localInstantMillis, localMidnightUtcMillis, localMinuteOfDay, utcMillisToLocalEpochDay } from '../src/localDay';

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

const utcDay = (y: number, m: number, d: number) => epochDay(y, m, d);

describe('utcMillisToLocalEpochDay (local calendar date epoch day)', () => {
  it.each([
    ['Europe/Madrid summer', 'Europe/Madrid', '2024-07-01T12:00:00Z', utcDay(2024, 7, 1)],
    ['Asia/Tokyo', 'Asia/Tokyo', '2024-07-01T12:00:00Z', utcDay(2024, 7, 1)],
    ['Buenos Aires', 'America/Argentina/Buenos_Aires', '2024-07-01T12:00:00Z', utcDay(2024, 7, 1)],
    ['UTC', 'UTC', '2024-07-01T12:00:00Z', utcDay(2024, 7, 1)],
    ['Madrid just after local midnight', 'Europe/Madrid', '2024-06-30T22:00:00Z', utcDay(2024, 7, 1)],
    ['Madrid just before local midnight', 'Europe/Madrid', '2024-06-30T21:59:59Z', utcDay(2024, 6, 30)],
    ['Kiritimati UTC+14', 'Pacific/Kiritimati', '2026-08-03T10:00:00Z', utcDay(2026, 8, 4)],
  ])('%s', (_name, zone, iso, expected) => {
    expect(utcMillisToLocalEpochDay(Date.parse(iso), zone)).toBe(expected);
  });

  it('maps consecutive London dates to consecutive days across both DST changes', () => {
    const noon = (y: number, m: number, d: number) => Date.UTC(y, m - 1, d, 12);
    const zone = 'Europe/London';
    expect(utcMillisToLocalEpochDay(noon(2026, 3, 30), zone)).toBe(utcMillisToLocalEpochDay(noon(2026, 3, 29), zone) + 1);
    expect(utcMillisToLocalEpochDay(noon(2026, 10, 26), zone)).toBe(utcMillisToLocalEpochDay(noon(2026, 10, 25), zone) + 1);
    // Local midnight at BST start/end instants still belongs to the new date.
    expect(utcMillisToLocalEpochDay(Date.parse('2026-03-29T23:00:00Z'), zone)).toBe(utcDay(2026, 3, 30));
    expect(utcMillisToLocalEpochDay(Date.parse('2026-10-26T00:00:00Z'), zone)).toBe(utcDay(2026, 10, 26));
  });

  it('round-trips through localMidnightUtcMillis / localInstantMillis', () => {
    for (const zone of ['Europe/Madrid', 'Europe/London', 'Asia/Tokyo', 'America/New_York', 'Pacific/Kiritimati', 'UTC']) {
      for (const iso of ['2024-03-31T10:00:00Z', '2026-03-29T12:00:00Z', '2026-10-25T12:00:00Z', '2024-11-03T12:00:00Z']) {
        const day = utcMillisToLocalEpochDay(Date.parse(iso), zone);
        const midnight = localMidnightUtcMillis(day, zone);
        expect(utcMillisToLocalEpochDay(midnight, zone)).toBe(day);
        expect(utcMillisToLocalEpochDay(midnight - 1, zone)).toBe(day - 1);
        expect(utcMillisToLocalEpochDay(localInstantMillis(day, zone, 20 * 60), zone)).toBe(day);
      }
    }
  });

  it('maps Madrid epochDay to the local date (09:00 on 2024-06-30 is 07:00Z)', () => {
    expect(new Date(localInstantMillis(utcDay(2024, 6, 30), 'Europe/Madrid', 9 * 60)).toISOString()).toBe(
      '2024-06-30T07:00:00.000Z',
    );
  });
});
