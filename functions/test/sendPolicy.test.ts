import { describe, expect, it } from 'vitest';

import { evaluateSendEligibility, notificationTtl, type SendTimeSettings } from '../src/sendPolicy';

const LOCAL_DAY = Math.floor(Date.UTC(2026, 7, 10) / 86_400_000);
const NOON_UTC = Date.UTC(2026, 7, 10, 12);

const enabledSettings: SendTimeSettings = {
  remindersEnabled: true,
  reflectionEnabled: true,
  moodEnabled: true,
  streakEnabled: true,
  healerEnabled: true,
  meditationReturnEnabled: true,
  quietHoursEnabled: false,
  quietHoursStartMinute: 23 * 60,
  quietHoursEndMinute: 7 * 60,
  timeZone: 'UTC',
};

const baseInput = {
  localDay: LOCAL_DAY,
  settings: enabledSettings,
  nowMillis: NOON_UTC,
  moodAlreadyLogged: false,
  completions: [],
  healerUses: [],
  affirmationDoneToday: false,
  compassAnsweredToday: false,
  meditationReturnState: {
    absenceStartLocalDay: null as number | null,
    lastSentLocalDay: null as number | null,
    lastBand: null as 'inactive_3_4' | 'inactive_7_10' | null,
  },
  moodDeliveredAtMillis: null as number | null,
  familyAlreadyDeliveredToday: false,
};

describe('evaluateSendEligibility', () => {
  it('skips a queued task when its configurable channel is now disabled', () => {
    const result = evaluateSendEligibility({
      ...baseInput,
      channel: 'reminder',
      settings: { ...enabledSettings, remindersEnabled: false },
    });

    expect(result).toEqual({ eligible: false, reason: 'channel-disabled' });
  });

  // spec: "Settings Toggles for Streak-Risk, Healer, and Meditation Return".
  it.each([
    ['streak', 'streakEnabled'],
    ['healer', 'healerEnabled'],
    ['meditation_return', 'meditationReturnEnabled'],
  ] as const)('skips %s when its toggle is explicitly disabled', (channel, settingsKey) => {
    const result = evaluateSendEligibility({
      ...baseInput,
      channel,
      settings: { ...enabledSettings, [settingsKey]: false },
    });

    expect(result).toEqual({ eligible: false, reason: 'channel-disabled' });
  });

  // design D8's critical default-true gotcha, verified at the pure `isChannelEnabled` boundary:
  // a settings object with these already resolved to `true` (the caller's job at the Firestore
  // read site, per D8) MUST be treated as enabled -- this pins the interface contract so a future
  // caller can't regress the resolution without this test catching it downstream too.
  it.each(['streak', 'healer', 'meditation_return'] as const)(
    '%s is eligible when its toggle is enabled',
    (channel) => {
      const result = evaluateSendEligibility({ ...baseInput, channel, settings: enabledSettings });

      expect(result.reason).not.toBe('channel-disabled');
    },
  );

  it('skips a queued task when the current local time is now inside quiet hours', () => {
    const result = evaluateSendEligibility({
      ...baseInput,
      channel: 'reflection',
      settings: {
        ...enabledSettings,
        quietHoursEnabled: true,
        quietHoursStartMinute: 11 * 60,
        quietHoursEndMinute: 13 * 60,
      },
    });

    expect(result).toEqual({ eligible: false, reason: 'quiet-hours' });
  });

  it('skips a streak task when today became fully complete after planning', () => {
    const result = evaluateSendEligibility({
      ...baseInput,
      channel: 'streak',
      completions: [
        { epochDay: LOCAL_DAY - 1, meditationDone: true, affirmationDone: true },
        { epochDay: LOCAL_DAY, meditationDone: true, affirmationDone: true },
      ],
    });

    expect(result).toEqual({ eligible: false, reason: 'streak-no-longer-at-risk' });
  });

  it('skips a healer task when the healer was activated after planning', () => {
    const result = evaluateSendEligibility({
      ...baseInput,
      channel: 'healer',
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
      ...baseInput,
      channel: 'mood',
      moodAlreadyLogged: true,
    });

    expect(result).toEqual({ eligible: false, reason: 'mood-already-logged' });
  });

  it('skips a task once its target local day has ended', () => {
    const result = evaluateSendEligibility({
      ...baseInput,
      channel: 'reminder',
      nowMillis: Date.UTC(2026, 7, 11),
    });

    expect(result).toEqual({ eligible: false, reason: 'target-day-expired' });
  });

  // design §3 table row "≤1 delivery per family per local day" -- generic, applies to any channel.
  describe('family-already-delivered-today', () => {
    it('skips any channel once its family already delivered today', () => {
      const result = evaluateSendEligibility({
        ...baseInput,
        channel: 'reminder',
        familyAlreadyDeliveredToday: true,
      });

      expect(result).toEqual({ eligible: false, reason: 'family-already-delivered-today' });
    });

    it('does not skip when the family has not delivered today', () => {
      const result = evaluateSendEligibility({
        ...baseInput,
        channel: 'reminder',
        familyAlreadyDeliveredToday: false,
      });

      expect(result).toEqual({ eligible: true });
    });
  });

  // design §3 table row "Mood <2h ago postpones/suppresses Compass" -- send-time half.
  describe('compass-too-soon-after-mood', () => {
    it('skips a reflection send when mood was delivered less than 2h ago', () => {
      const result = evaluateSendEligibility({
        ...baseInput,
        channel: 'reflection',
        moodDeliveredAtMillis: NOON_UTC - 60 * 60_000, // 1h ago
      });

      expect(result).toEqual({ eligible: false, reason: 'compass-too-soon-after-mood' });
    });

    it('allows a reflection send when mood was delivered exactly at the 2h boundary', () => {
      const result = evaluateSendEligibility({
        ...baseInput,
        channel: 'reflection',
        moodDeliveredAtMillis: NOON_UTC - 2 * 60 * 60_000,
      });

      expect(result).toEqual({ eligible: true });
    });

    it('does not apply the mood-spacing check to non-reflection channels', () => {
      const result = evaluateSendEligibility({
        ...baseInput,
        channel: 'reminder',
        moodDeliveredAtMillis: NOON_UTC - 60 * 60_000,
      });

      expect(result).toEqual({ eligible: true });
    });

    it('does not skip reflection when mood was never delivered', () => {
      const result = evaluateSendEligibility({
        ...baseInput,
        channel: 'reflection',
        moodDeliveredAtMillis: null,
      });

      expect(result).toEqual({ eligible: true });
    });
  });

  // design §3 table row "Recent relevant activity suppresses the corresponding reminder".
  describe('activity-already-done', () => {
    it('skips a reminder send when the affirmation was already done today', () => {
      const result = evaluateSendEligibility({
        ...baseInput,
        channel: 'reminder',
        affirmationDoneToday: true,
      });

      expect(result).toEqual({ eligible: false, reason: 'activity-already-done' });
    });

    it('skips a reflection send when the compass question was already answered today', () => {
      const result = evaluateSendEligibility({
        ...baseInput,
        channel: 'reflection',
        compassAnsweredToday: true,
      });

      expect(result).toEqual({ eligible: false, reason: 'activity-already-done' });
    });

    it('does not skip a reminder when the affirmation is not yet done', () => {
      const result = evaluateSendEligibility({
        ...baseInput,
        channel: 'reminder',
        affirmationDoneToday: false,
      });

      expect(result).toEqual({ eligible: true });
    });
  });

  // design §4: send-time re-check of `shouldFireMeditationReturn`, matching the plan-time decision.
  describe('meditation-return-not-due', () => {
    it('skips a meditation_return send when the user already meditated today', () => {
      const result = evaluateSendEligibility({
        ...baseInput,
        channel: 'meditation_return',
        completions: [{ epochDay: LOCAL_DAY, meditationDone: true, affirmationDone: false }],
      });

      expect(result).toEqual({ eligible: false, reason: 'meditation-return-not-due' });
    });

    it('allows a meditation_return send when the inactivity band is still due', () => {
      const result = evaluateSendEligibility({
        ...baseInput,
        channel: 'meditation_return',
        completions: [{ epochDay: LOCAL_DAY - 4, meditationDone: true, affirmationDone: false }],
      });

      expect(result).toEqual({ eligible: true });
    });

    it('skips a meditation_return send once cooled down for the same band/absence', () => {
      const result = evaluateSendEligibility({
        ...baseInput,
        channel: 'meditation_return',
        completions: [{ epochDay: LOCAL_DAY - 4, meditationDone: true, affirmationDone: false }],
        meditationReturnState: {
          absenceStartLocalDay: LOCAL_DAY - 3,
          lastSentLocalDay: LOCAL_DAY - 1,
          lastBand: 'inactive_3_4',
        },
      });

      expect(result).toEqual({ eligible: false, reason: 'meditation-return-not-due' });
    });

    it('does not apply the meditation-return check to other channels', () => {
      const result = evaluateSendEligibility({
        ...baseInput,
        channel: 'reminder',
        completions: [{ epochDay: LOCAL_DAY, meditationDone: true, affirmationDone: false }],
      });

      expect(result).toEqual({ eligible: true });
    });
  });
});

describe('notificationTtl', () => {
  it('uses a shorter channel-specific cap for a time-sensitive streak alert', () => {
    expect(notificationTtl('reminder', LOCAL_DAY, 'UTC', NOON_UTC)).toBe('14400s');
    expect(notificationTtl('streak', LOCAL_DAY, 'UTC', NOON_UTC)).toBe('3600s');
  });

  it('caps meditation_return at 12h (design §7)', () => {
    expect(notificationTtl('meditation_return', LOCAL_DAY, 'UTC', NOON_UTC)).toBe('43200s');
  });

  it('expires at the end of the target local day and rejects an already-expired target day', () => {
    const thirtyMinutesBeforeMidnight = Date.UTC(2026, 7, 10, 23, 30);
    const nextMidnight = Date.UTC(2026, 7, 11);

    expect(notificationTtl('reminder', LOCAL_DAY, 'UTC', thirtyMinutesBeforeMidnight)).toBe('1800s');
    expect(notificationTtl('reminder', LOCAL_DAY, 'UTC', nextMidnight)).toBeNull();
  });
});
