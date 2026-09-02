import { shouldFireHealerAlert, type HealerUse } from './healer';
import { localInstantMillis, localMinuteOfDay } from './localDay';
import { shouldFireMeditationReturn, type MeditationReturnState } from './meditationReturn';
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
  // design §3 suppression/priority decision table (`notification-orchestration`).
  COMPASS_TOO_SOON_AFTER_MOOD: 'compass-too-soon-after-mood',
  FAMILY_ALREADY_DELIVERED_TODAY: 'family-already-delivered-today',
  ACTIVITY_ALREADY_DONE: 'activity-already-done',
  /** design §4 (`meditation-return`'s own send-time re-check, matching the plan-time decision). */
  MEDITATION_RETURN_NOT_DUE: 'meditation-return-not-due',
} as const;

/** design §3 row: "Mood <2h ago postpones/suppresses Compass" -- send-time half. */
const COMPASS_AFTER_MOOD_MIN_GAP_MS = 2 * 60 * 60_000;

const TTL_CAP_SECONDS = {
  reminder: 4 * 60 * 60,
  reflection: 4 * 60 * 60,
  mood: 2 * 60 * 60,
  streak: 60 * 60,
  healer: 60 * 60,
  // design §7: "~1-2 days", floored by the existing target-day-end cap.
  meditation_return: 12 * 60 * 60,
} as const satisfies Record<NotificationChannel, number>;

export type SendSkipReason = (typeof SEND_SKIP_REASON)[keyof typeof SEND_SKIP_REASON];

export interface SendTimeSettings {
  remindersEnabled: boolean;
  reflectionEnabled: boolean;
  moodEnabled: boolean;
  /** Settings Toggles requirement (design §10) -- the already-resolved (default-true-when-absent)
   *  value, same convention as `NotificationSettings`. */
  streakEnabled: boolean;
  healerEnabled: boolean;
  meditationReturnEnabled: boolean;
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
  /** `reminder`'s "already done" check (design §3 table). */
  affirmationDoneToday: boolean;
  /** `reflection`'s "already done" check (design §3 table, D9's `compassAnswers.ts`). */
  compassAnsweredToday: boolean;
  /** `meditation_return`'s cooldown state (design §4, `notificationState/current.meditationReturn`),
   *  re-evaluated at send time via `shouldFireMeditationReturn`. */
  meditationReturnState: MeditationReturnState;
  /** From `notificationDeliveries/{localDay}.families.mood.deliveredAtMillis`; `null` when Mood
   *  has not delivered today. Drives the `reflection`-only compass-too-soon-after-mood check. */
  moodDeliveredAtMillis: number | null;
  /** From `notificationDeliveries/{localDay}.families[channel]` presence -- the generic "≤1
   *  delivery per family per local day" frequency cap (design §3 table). */
  familyAlreadyDeliveredToday: boolean;
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
      return settings.streakEnabled;
    case 'healer':
      return settings.healerEnabled;
    case 'meditation_return':
      return settings.meditationReturnEnabled;
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
  if (
    input.channel === 'meditation_return' &&
    !shouldFireMeditationReturn(input.completions, input.localDay, input.meditationReturnState).fire
  ) {
    return { eligible: false, reason: SEND_SKIP_REASON.MEDITATION_RETURN_NOT_DUE };
  }
  if (input.channel === 'mood' && input.moodAlreadyLogged) {
    return { eligible: false, reason: SEND_SKIP_REASON.MOOD_ALREADY_LOGGED };
  }
  if (input.familyAlreadyDeliveredToday) {
    return { eligible: false, reason: SEND_SKIP_REASON.FAMILY_ALREADY_DELIVERED_TODAY };
  }
  if (
    input.channel === 'reflection' &&
    input.moodDeliveredAtMillis !== null &&
    input.nowMillis - input.moodDeliveredAtMillis < COMPASS_AFTER_MOOD_MIN_GAP_MS
  ) {
    return { eligible: false, reason: SEND_SKIP_REASON.COMPASS_TOO_SOON_AFTER_MOOD };
  }
  if (input.channel === 'reminder' && input.affirmationDoneToday) {
    return { eligible: false, reason: SEND_SKIP_REASON.ACTIVITY_ALREADY_DONE };
  }
  if (input.channel === 'reflection' && input.compassAnsweredToday) {
    return { eligible: false, reason: SEND_SKIP_REASON.ACTIVITY_ALREADY_DONE };
  }
  return { eligible: true };
}
