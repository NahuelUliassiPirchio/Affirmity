import { shouldFireHealerAlert, type HealerUse } from './healer';
import { localInstantMillis, localMinuteOfDay } from './localDay';
import type { NotificationChannel } from './planner';
import { isWithinQuietHours } from './schedule';
import { shouldFireStreakAlert, type Completion } from './streak';

const SEND_SKIP_REASON = {
  CHANNEL_DISABLED: 'channel-disabled',
  QUIET_HOURS: 'quiet-hours',
  TARGET_DAY_EXPIRED: 'target-day-expired',
  MOOD_ALREADY_LOGGED: 'mood-already-logged',
  STREAK_NO_LONGER_AT_RISK: 'streak-no-longer-at-risk',
  HEALER_NO_LONGER_AVAILABLE: 'healer-no-longer-available',
  TIME_ZONE_MISSING: 'time-zone-missing',
} as const;

const TTL_CAP_SECONDS = {
  reminder: 4 * 60 * 60,
  reflection: 4 * 60 * 60,
  mood: 2 * 60 * 60,
  streak: 60 * 60,
  healer: 60 * 60,
} as const satisfies Record<NotificationChannel, number>;

export type SendSkipReason = (typeof SEND_SKIP_REASON)[keyof typeof SEND_SKIP_REASON];

export interface SendTimeSettings {
  remindersEnabled: boolean;
  reflectionEnabled: boolean;
  moodEnabled: boolean;
  quietHoursEnabled: boolean;
  quietHoursStartMinute: number;
  quietHoursEndMinute: number;
  timeZone: string | null;
}

export interface SendEligibilityInput {
  channel: NotificationChannel;
  localDay: number;
  settings: SendTimeSettings;
  nowMillis: number;
  moodAlreadyLogged: boolean;
  completions: Completion[];
  healerUses: HealerUse[];
}

export interface SendEligibilityResult {
  eligible: boolean;
  reason?: SendSkipReason;
}

/** FCM TTL bounded by both channel urgency and the end of the task's target local day. */
export function notificationTtl(
  channel: NotificationChannel,
  localDay: number,
  zone: string,
  nowMillis: number,
): string | null {
  const targetDayEndMillis = localInstantMillis(localDay + 1, zone, 0);
  const remainingSeconds = Math.floor((targetDayEndMillis - nowMillis) / 1000);
  if (remainingSeconds <= 0) return null;
  return `${Math.min(remainingSeconds, TTL_CAP_SECONDS[channel])}s`;
}

function isChannelEnabled(channel: NotificationChannel, settings: SendTimeSettings): boolean {
  switch (channel) {
    case 'reminder':
      return settings.remindersEnabled;
    case 'reflection':
      return settings.reflectionEnabled;
    case 'mood':
      return settings.moodEnabled;
    case 'streak':
    case 'healer':
      return true;
  }
}

/** Pure send-time guard applied after re-reading the user's latest Firestore state. */
export function evaluateSendEligibility(input: SendEligibilityInput): SendEligibilityResult {
  if (!isChannelEnabled(input.channel, input.settings)) {
    return { eligible: false, reason: SEND_SKIP_REASON.CHANNEL_DISABLED };
  }
  const zone = input.settings.timeZone;
  if (!zone) return { eligible: false, reason: SEND_SKIP_REASON.TIME_ZONE_MISSING };
  if (
    input.settings.quietHoursEnabled &&
    isWithinQuietHours(
      localMinuteOfDay(input.nowMillis, zone),
      input.settings.quietHoursStartMinute,
      input.settings.quietHoursEndMinute,
    )
  ) {
    return { eligible: false, reason: SEND_SKIP_REASON.QUIET_HOURS };
  }
  if (!notificationTtl(input.channel, input.localDay, zone, input.nowMillis)) {
    return { eligible: false, reason: SEND_SKIP_REASON.TARGET_DAY_EXPIRED };
  }
  if (input.channel === 'streak' && !shouldFireStreakAlert(input.completions, input.localDay)) {
    return { eligible: false, reason: SEND_SKIP_REASON.STREAK_NO_LONGER_AT_RISK };
  }
  if (
    input.channel === 'healer' &&
    !shouldFireHealerAlert(input.completions, input.healerUses, input.localDay)
  ) {
    return { eligible: false, reason: SEND_SKIP_REASON.HEALER_NO_LONGER_AVAILABLE };
  }
  if (input.channel === 'mood' && input.moodAlreadyLogged) {
    return { eligible: false, reason: SEND_SKIP_REASON.MOOD_ALREADY_LOGGED };
  }
  return { eligible: true };
}
