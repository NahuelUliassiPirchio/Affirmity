import { describe, expect, it } from 'vitest';

import { evaluateSendEligibility, notificationTtl, type SendTimeSettings } from '../src/sendPolicy';

const LOCAL_DAY = Math.floor(Date.UTC(2026, 7, 10) / 86_400_000);
const NOON_UTC = Date.UTC(2026, 7, 10, 12);

const enabledSettings: SendTimeSettings = {
  remindersEnabled: true,
  reflectionEnabled: true,
  moodEnabled: true,
  quietHoursEnabled: false,
  quietHoursStartMinute: 23 * 60,
  quietHoursEndMinute: 7 * 60,
  timeZone: 'UTC',
};

describe('evaluateSendEligibility', () => {
  it('skips a queued task when its configurable channel is now disabled', () => {
    const result = evaluateSendEligibility({
      channel: 'reminder',
      localDay: LOCAL_DAY,
      settings: { ...enabledSettings, remindersEnabled: false },
      nowMillis: NOON_UTC,
      moodAlreadyLogged: false,
      completions: [],
      healerUses: [],
    });

    expect(result).toEqual({ eligible: false, reason: 'channel-disabled' });
  });

  it('skips a queued task when the current local time is now inside quiet hours', () => {
    const result = evaluateSendEligibility({
      channel: 'reflection',
      localDay: LOCAL_DAY,
      settings: {
        ...enabledSettings,
        quietHoursEnabled: true,
        quietHoursStartMinute: 11 * 60,
        quietHoursEndMinute: 13 * 60,
      },
      nowMillis: NOON_UTC,
      moodAlreadyLogged: false,
      completions: [],
      healerUses: [],
    });

    expect(result).toEqual({ eligible: false, reason: 'quiet-hours' });
  });

  it('skips a streak task when today became fully complete after planning', () => {
    const result = evaluateSendEligibility({
      channel: 'streak',
      localDay: LOCAL_DAY,
      settings: enabledSettings,
      nowMillis: NOON_UTC,
      moodAlreadyLogged: false,
      completions: [
        { epochDay: LOCAL_DAY - 1, meditationDone: true, affirmationDone: true },
        { epochDay: LOCAL_DAY, meditationDone: true, affirmationDone: true },
      ],
      healerUses: [],
    });

    expect(result).toEqual({ eligible: false, reason: 'streak-no-longer-at-risk' });
  });

  it('skips a healer task when the healer was activated after planning', () => {
    const result = evaluateSendEligibility({
      channel: 'healer',
      localDay: LOCAL_DAY,
      settings: enabledSettings,
      nowMillis: NOON_UTC,
      moodAlreadyLogged: false,
      completions: [
        { epochDay: LOCAL_DAY - 3, meditationDone: true, affirmationDone: true },
        { epochDay: LOCAL_DAY - 2, meditationDone: true, affirmationDone: true },
      ],
      healerUses: [{ healedEpochDay: LOCAL_DAY - 1 }],
    });

    expect(result).toEqual({ eligible: false, reason: 'healer-no-longer-available' });
  });

  it('skips a mood task when today already has a mood entry', () => {
    const result = evaluateSendEligibility({
      channel: 'mood',
      localDay: LOCAL_DAY,
      settings: enabledSettings,
      nowMillis: NOON_UTC,
      moodAlreadyLogged: true,
      completions: [],
      healerUses: [],
    });

    expect(result).toEqual({ eligible: false, reason: 'mood-already-logged' });
  });

  it('skips a task once its target local day has ended', () => {
    const result = evaluateSendEligibility({
      channel: 'reminder',
      localDay: LOCAL_DAY,
      settings: enabledSettings,
      nowMillis: Date.UTC(2026, 7, 11),
      moodAlreadyLogged: false,
      completions: [],
      healerUses: [],
    });

    expect(result).toEqual({ eligible: false, reason: 'target-day-expired' });
  });
});

describe('notificationTtl', () => {
  it('uses a shorter channel-specific cap for a time-sensitive streak alert', () => {
    expect(notificationTtl('reminder', LOCAL_DAY, 'UTC', NOON_UTC)).toBe('14400s');
    expect(notificationTtl('streak', LOCAL_DAY, 'UTC', NOON_UTC)).toBe('3600s');
  });

  it('expires at the end of the target local day and rejects an already-expired target day', () => {
    const thirtyMinutesBeforeMidnight = Date.UTC(2026, 7, 10, 23, 30);
    const nextMidnight = Date.UTC(2026, 7, 11);

    expect(notificationTtl('reminder', LOCAL_DAY, 'UTC', thirtyMinutesBeforeMidnight)).toBe('1800s');
    expect(notificationTtl('reminder', LOCAL_DAY, 'UTC', nextMidnight)).toBeNull();
  });
});
