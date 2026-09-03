/**
 * Cloud Functions entry points: `planNotifications` (Cloud Scheduler-triggered) and
 * `sendNotification` (Cloud Tasks-triggered, OIDC-checked). Deployment/live wiring is out of
 * scope for this PR (no Firebase project/credentials in this environment, per design.md's
 * "User-owned prerequisites") -- this file is written to a reasonable production shape but is
 * intentionally not unit-tested; the pure logic it wires (schedule/localDay/streak/planner/
 * tasks/fcm/sendPolicy) is covered by the Vitest suites next to it.
 */

import { initializeApp, getApps } from 'firebase-admin/app';
import { getFirestore, FieldValue, type QueryDocumentSnapshot } from 'firebase-admin/firestore';
import { getMessaging } from 'firebase-admin/messaging';
import { getAuth } from 'firebase-admin/auth';
import { onSchedule } from 'firebase-functions/v2/scheduler';
import { onRequest } from 'firebase-functions/v2/https';
import { CloudTasksClient as GoogleCloudTasksClient } from '@google-cloud/tasks';
import { OAuth2Client } from 'google-auth-library';
import { google } from 'googleapis';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import {
  Environment as AppStoreEnvironment,
  SignedDataVerifier,
  VerificationException,
  VerificationStatus,
} from '@apple/app-store-server-library';

import { localHourInZone, localMinuteOfDay, utcMillisToLocalEpochDay } from './localDay';
import {
  planAllUsers,
  type NotificationChannel,
  type PlanStore,
  type PlanResult,
  type TaskEnqueuer,
  type UserPlanInput,
} from './planner';
import { taskName } from './tasks';
import { hasTransientFcmFailures, sendAndPrune, type FcmClient, type TokenStore } from './fcm';
import { evaluateSendEligibility, notificationTtl, type SendTimeSettings } from './sendPolicy';
import { currentStreak, streakBand, type Completion } from './streak';
import { isHealerExpiringToday, type HealerUse } from './healer';
import { shouldFireMeditationReturn, type MeditationReturnState } from './meditationReturn';
import {
  loadCopyCatalog,
  renderCopy,
  selectVariant,
  type CatalogSource,
  type CopyLocale,
  type CopyText,
  type CopyVariant,
  type NotificationFamily,
} from './copyCatalog';
import {
  buildDeliveryLogUpdate,
  buildVariantHistoryUpdate,
  type DeliveryLogDoc,
  type DeliveryLogEntry,
  type NotificationStateDoc,
} from './notificationState';
import { wasCompassAnsweredToday, buildCompassAnswerDoc, type CompassAnswerDoc } from './compassAnswers';
import {
  handleRtdn,
  resolveEntitlement,
  type EntitlementDoc,
  type EntitlementStore,
  type OidcClaims,
  type PlayApiClient,
  type PlaySubscriptionV2,
} from './billing';
import {
  AppStoreVerificationError,
  resolveIosEntitlement,
  type AppStoreVerifier,
} from './appStoreBilling';

export * from './schedule';
export * from './localDay';
export * from './streak';
export * from './planner';
export * from './tasks';
export * from './fcm';
export * from './sendPolicy';
export * from './billing';

if (getApps().length === 0) {
  initializeApp();
}

/** Local hour at which the planner considers a user "due" for tomorrow's/today's plan.
 * Overridable via PLANNING_LOCAL_HOUR_OVERRIDE for manual end-to-end testing outside the real
 * window -- remove the env var (or leave it unset) to fall back to the real production hour. */
const PLANNING_LOCAL_HOUR = process.env.PLANNING_LOCAL_HOUR_OVERRIDE
  ? Number(process.env.PLANNING_LOCAL_HOUR_OVERRIDE)
  : 3;

// Deploy-time configuration -- set via `firebase functions:config:set` / environment config, not
// hardcoded, since queue path/invoker identity differ per environment (dev/staging/prod).
const QUEUE_PATH = process.env.NOTIFICATION_QUEUE_PATH ?? '';
const SEND_URL = process.env.SEND_NOTIFICATION_URL ?? '';
const INVOKER_SERVICE_ACCOUNT = process.env.NOTIFICATION_INVOKER_SA ?? '';

const tasksClient = new GoogleCloudTasksClient();
const oauthClient = new OAuth2Client();

function firestoreStore(): PlanStore {
  const db = getFirestore();
  return {
    async hasPlan(uid, localDay) {
      const doc = await db.doc(`users/${uid}/notificationPlans/${localDay}`).get();
      return doc.exists && doc.data()?.status === 'planned';
    },
    async markPlanned(uid, localDay, result: PlanResult) {
      await db.doc(`users/${uid}/notificationPlans/${localDay}`).set({
        localDay,
        plannedAt: FieldValue.serverTimestamp(),
        status: 'planned',
        slots: result.tasks,
      });
    },
    async markFailed(uid, localDay, error) {
      await db.doc(`users/${uid}/notificationPlans/${localDay}`).set(
        { localDay, plannedAt: FieldValue.serverTimestamp(), status: 'failed', error },
        { merge: true },
      );
    },
  };
}

function cloudTasksEnqueuer(): TaskEnqueuer {
  return {
    async enqueue(task) {
      const name = `${QUEUE_PATH}/tasks/${taskName(task.uid, task.localDay, task.channel, task.slot)}`;
      try {
        await tasksClient.createTask({
          parent: QUEUE_PATH,
          task: {
            name,
            httpRequest: {
              httpMethod: 'POST',
              url: SEND_URL,
              headers: { 'Content-Type': 'application/json' },
              body: Buffer.from(JSON.stringify(task)).toString('base64'),
              oidcToken: { serviceAccountEmail: INVOKER_SERVICE_ACCOUNT },
            },
            scheduleTime: { seconds: Math.floor(task.atMillis / 1000) },
          },
        });
        return { created: true };
      } catch (err) {
        // gRPC code 6 = ALREADY_EXISTS: the deterministic task name means this is a duplicate
        // planning attempt, not a real failure -- design.md's idempotency decision.
        if (err && typeof err === 'object' && (err as { code?: number }).code === 6) {
          return { created: false };
        }
        throw err;
      }
    },
  };
}

const EMPTY_MEDITATION_RETURN_STATE: MeditationReturnState = {
  absenceStartLocalDay: null,
  lastSentLocalDay: null,
  lastBand: null,
};

/** Reads `users/{uid}/notificationState/current.meditationReturn` (design §2/§4), defaulting to an
 * empty cooldown state when the document or field is missing (new users, first-ever evaluation).
 * Used at PLAN time only -- `sendNotification` (send time) reads the full doc via
 * `loadNotificationState` below, since it also needs `variantHistory` for anti-repeat. */
async function loadMeditationReturnState(uid: string): Promise<MeditationReturnState> {
  const db = getFirestore();
  const doc = await db.doc(`users/${uid}/notificationState/current`).get();
  const stored = doc.data()?.meditationReturn as MeditationReturnState | undefined;
  return stored ?? EMPTY_MEDITATION_RETURN_STATE;
}

/** Full `users/{uid}/notificationState/current` read (design §2), used at SEND time by
 * `sendNotification`: `variantHistory` drives anti-repeat selection for every channel;
 * `meditationReturn` drives the meditation-return-specific cooldown re-check. */
async function loadNotificationState(uid: string): Promise<NotificationStateDoc> {
  const db = getFirestore();
  const doc = await db.doc(`users/${uid}/notificationState/current`).get();
  const data = doc.data();
  return {
    variantHistory: (data?.variantHistory as NotificationStateDoc['variantHistory']) ?? {},
    meditationReturn: (data?.meditationReturn as MeditationReturnState | undefined) ?? EMPTY_MEDITATION_RETURN_STATE,
  };
}

/** `users/{uid}/notificationDeliveries/{localDay}` read (design §2/§3) -- today's cross-family
 * delivery log, feeding the "compass-too-soon-after-mood" and "≤1/family/day" send-time rules. */
async function loadDeliveryLog(uid: string, localDay: number): Promise<DeliveryLogDoc | undefined> {
  const db = getFirestore();
  const doc = await db.doc(`users/${uid}/notificationDeliveries/${localDay}`).get();
  return doc.exists ? (doc.data() as DeliveryLogDoc) : undefined;
}

/** `users/{uid}/compassAnswers/{localDay}` read (design D9) -- feeds the reflection family's
 * send-time "already answered today" check. The write side is a client-owned Firestore write on
 * answer (out of this Cloud Functions file's scope; see design D9 / `compassAnswers.ts`). */
async function loadCompassAnswer(uid: string, localDay: number): Promise<CompassAnswerDoc | null> {
  const db = getFirestore();
  const doc = await db.doc(`users/${uid}/compassAnswers/${localDay}`).get();
  return doc.exists ? (doc.data() as CompassAnswerDoc) : null;
}

/** Admin-SDK-backed `CatalogSource` (design §1): one `.where('enabled','==',true).get()`, memoized
 * by `loadCopyCatalog`'s own 10-minute module-scope cache. */
function firestoreCatalogSource(): CatalogSource {
  return {
    async loadEnabledVariants() {
      const db = getFirestore();
      const snap = await db.collection('notificationCopy').where('enabled', '==', true).get();
      return snap.docs.map((d: QueryDocumentSnapshot) => d.data() as CopyVariant);
    },
  };
}

/** Local-hour cut for Mood's two copy contexts (design §1 Context Filtering: "Mood by
 * afternoon/evening"). 18:00 matches `schedule.ts`'s `noche` segment boundary. */
const MOOD_EVENING_START_MINUTE = 18 * 60;

const DESTINATION_BY_CHANNEL: Record<NotificationChannel, string> = {
  reminder: 'affirmations_feed',
  mood: 'mood_checkin',
  reflection: 'compass_question',
  streak: 'streak_action',
  healer: 'healer_flow',
  meditation_return: 'short_meditation',
};

const CTA_KEY_BY_CHANNEL: Record<NotificationChannel, string> = {
  reminder: 'cta_affirmations',
  mood: 'cta_mood',
  reflection: 'cta_compass',
  streak: 'cta_streak',
  healer: 'cta_healer',
  meditation_return: 'cta_meditation',
};

async function loadActiveUserInputs(): Promise<UserPlanInput[]> {
  const db = getFirestore();
  // Every document under users/{uid}/settings/ is the single `preferences` doc (per
  // FirestorePaths.kt), so no per-doc-id filter is needed -- and collectionGroup queries can't
  // filter FieldPath.documentId() by a bare doc id anyway (it requires a full document path,
  // which isn't knowable without the uid this query is trying to discover).
  const settingsDocs = await db.collectionGroup('settings').get();

  const inputs: UserPlanInput[] = [];
  for (const doc of settingsDocs.docs) {
    const uid = doc.ref.parent.parent?.id;
    if (!uid) continue;

    const data = doc.data();
    const zone: string | undefined = data.timeZone;
    if (!zone) continue; // no timezone captured yet -- cannot plan a local-day schedule
    if (localHourInZone(Date.now(), zone) !== PLANNING_LOCAL_HOUR) continue;

    // Each user's calendar day is computed in their own zone, not a single shared UTC day: for
    // negative-offset zones like Argentina, the UTC calendar can roll to day N up to ~3h before
    // that user's local calendar does, which would misplan the day this tick is meant to cover.
    const localDay = utcMillisToLocalEpochDay(Date.now(), zone);

    const completionsSnap = await db.collection(`users/${uid}/dailyCompletions`).get();
    const healerUsesSnap = await db.collection(`users/${uid}/streakHealerUses`).get();
    const meditationReturnState = await loadMeditationReturnState(uid);

    inputs.push({
      uid,
      localDay,
      // Field names mirror FirestoreMappers.kt's "${channel.prefsPrefix}_..." convention
      // (prefsPrefix "reminder"/"reflection"), not the interface's own camelCase property names.
      // "_segments" holds an array of DaySegment.key strings (e.g. "manana", "noche").
      settings: {
        remindersEnabled: Boolean(data.reminder_enabled),
        reflectionEnabled: Boolean(data.reflection_enabled),
        moodEnabled: Boolean(data.mood_enabled),
        // design D8: these three are new -- existing users have never written these fields, so
        // `Boolean(undefined) === false` would silently opt every current user out on deploy.
        // Absent or explicit `true` -> enabled; only an explicit `false` disables.
        streakEnabled: data.streak_enabled !== false,
        healerEnabled: data.healer_enabled !== false,
        meditationReturnEnabled: data.meditation_return_enabled !== false,
        reminderSegments: Array.isArray(data.reminder_segments) ? data.reminder_segments : [],
        reflectionSegments: Array.isArray(data.reflection_segments) ? data.reflection_segments : [],
        moodSegments: Array.isArray(data.mood_segments) ? data.mood_segments : [],
        quietHoursEnabled: Boolean(data.quietHours_enabled),
        quietHoursStartMinute: Number(data.quietHours_startMinute ?? 1380),
        quietHoursEndMinute: Number(data.quietHours_endMinute ?? 420),
        timeZone: zone,
      },
      completions: completionsSnap.docs.map((d: QueryDocumentSnapshot) => ({
        epochDay: Number(d.id),
        meditationDone: Boolean(d.data().meditationDone),
        affirmationDone: Boolean(d.data().affirmationDone),
      })),
      healerUses: healerUsesSnap.docs.map((d: QueryDocumentSnapshot) => ({
        healedEpochDay: Number(d.data().healedEpochDay ?? d.id),
      })),
      meditationReturnState,
    });
  }
  return inputs;
}

/** Hourly tick; plans users whose local hour has just reached `PLANNING_LOCAL_HOUR`. */
export const planNotifications = onSchedule('every 60 minutes', async () => {
  const inputs = await loadActiveUserInputs();
  await planAllUsers(inputs, firestoreStore(), cloudTasksEnqueuer());
});

async function verifyOidcCaller(authorizationHeader: string | undefined): Promise<boolean> {
  if (!authorizationHeader?.startsWith('Bearer ')) return false;
  const idToken = authorizationHeader.slice('Bearer '.length);
  try {
    const ticket = await oauthClient.verifyIdToken({ idToken, audience: SEND_URL });
    const payload = ticket.getPayload();
    return Boolean(payload?.email && payload.email === INVOKER_SERVICE_ACCOUNT);
  } catch {
    return false;
  }
}

/**
 * Cloud Tasks HTTP target. Rejects any request without a valid OIDC identity from the dedicated
 * invoker service account (design.md's Threat Matrix requirement -- no public invoker).
 */
export const sendNotification = onRequest(async (req, res) => {
  const authorized = await verifyOidcCaller(req.headers.authorization);
  if (!authorized) {
    res.status(401).send('Unauthorized');
    return;
  }

  const {
    uid,
    channel,
    localDay,
    title: legacyTitle,
    body: legacyBody,
    data: legacyData,
  } = req.body as {
    uid: string;
    channel: NotificationChannel;
    localDay?: number;
    title?: string;
    body?: string;
    data?: Record<string, string>;
  };
  const supportedChannels = new Set<NotificationChannel>([
    'reminder',
    'reflection',
    'mood',
    'streak',
    'healer',
    'meditation_return',
  ]);
  if (!uid || !supportedChannels.has(channel) || typeof localDay !== 'number') {
    res.status(400).send('Missing or invalid uid/channel/localDay');
    return;
  }

  const db = getFirestore();

  const settingsDoc = await db.doc(`users/${uid}/settings/preferences`).get();
  const settingsData = settingsDoc.data() ?? {};
  const settings: SendTimeSettings = {
    remindersEnabled: Boolean(settingsData.reminder_enabled),
    reflectionEnabled: Boolean(settingsData.reflection_enabled),
    moodEnabled: Boolean(settingsData.mood_enabled),
    // design D8: same default-true-when-absent rule as `loadActiveUserInputs` above -- these
    // three toggles must never silently opt an existing user out on deploy.
    streakEnabled: settingsData.streak_enabled !== false,
    healerEnabled: settingsData.healer_enabled !== false,
    meditationReturnEnabled: settingsData.meditation_return_enabled !== false,
    quietHoursEnabled: Boolean(settingsData.quietHours_enabled),
    quietHoursStartMinute: Number(settingsData.quietHours_startMinute ?? 1380),
    quietHoursEndMinute: Number(settingsData.quietHours_endMinute ?? 420),
    timeZone: typeof settingsData.timeZone === 'string' ? settingsData.timeZone : null,
  };
  // design §1: locale resolution -- missing/unsupported falls back to 'es'.
  const locale: CopyLocale = settingsData.locale === 'en' ? 'en' : 'es';

  const moodAlreadyLogged =
    channel === 'mood' ? (await db.doc(`users/${uid}/dailyMoods/${localDay}`).get()).exists : false;

  // Recomputed authoritatively at send time for every channel (design §7): the planner's own
  // `data.streakCount` hint is a plan-time guess, never trusted here.
  const completionsSnap = await db.collection(`users/${uid}/dailyCompletions`).get();
  const completions: Completion[] = completionsSnap.docs.map((completionDoc: QueryDocumentSnapshot) => ({
    epochDay: Number(completionDoc.id),
    meditationDone: Boolean(completionDoc.data().meditationDone),
    affirmationDone: Boolean(completionDoc.data().affirmationDone),
  }));

  const healerUses: HealerUse[] =
    channel === 'healer'
      ? (await db.collection(`users/${uid}/streakHealerUses`).get()).docs.map(
          (healerUseDoc: QueryDocumentSnapshot) => ({
            healedEpochDay: Number(healerUseDoc.data().healedEpochDay ?? healerUseDoc.id),
          }),
        )
      : [];

  // design §2: read once, needed for every channel's anti-repeat pool, not just meditation_return.
  const notificationState = await loadNotificationState(uid);
  const meditationReturnState = notificationState.meditationReturn ?? EMPTY_MEDITATION_RETURN_STATE;
  const meditationReturnDecision =
    channel === 'meditation_return' ? shouldFireMeditationReturn(completions, localDay, meditationReturnState) : null;

  const compassAnswerDoc = channel === 'reflection' ? await loadCompassAnswer(uid, localDay) : null;
  const compassAnsweredToday = wasCompassAnsweredToday(compassAnswerDoc, localDay);
  const affirmationDoneToday = completions.find((row) => row.epochDay === localDay)?.affirmationDone ?? false;

  const deliveryLog = await loadDeliveryLog(uid, localDay);
  const family: NotificationFamily = channel;
  const familyAlreadyDeliveredToday = Boolean(deliveryLog?.families?.[family]);
  const moodDeliveredAtMillis = deliveryLog?.families?.mood?.deliveredAtMillis ?? null;

  const sendCheckedAtMillis = Date.now();
  const eligibility = evaluateSendEligibility({
    channel,
    localDay,
    settings,
    nowMillis: sendCheckedAtMillis,
    moodAlreadyLogged,
    completions,
    healerUses,
    affirmationDoneToday,
    compassAnsweredToday,
    moodDeliveredAtMillis,
    familyAlreadyDeliveredToday,
    meditationReturnState,
  });
  if (!eligibility.eligible) {
    console.log(
      JSON.stringify({ event: 'notification_suppressed', uid, family, locale, reason: eligibility.reason }),
    );
    res.status(200).send(`Skipped: ${eligibility.reason}`);
    return;
  }

  const zone = settings.timeZone;
  if (!zone) {
    res.status(200).send('Skipped: time-zone-missing');
    return;
  }
  const ttl = notificationTtl(channel, localDay, zone, sendCheckedAtMillis);
  if (!ttl) {
    res.status(200).send('Skipped: target-day-expired');
    return;
  }

  // ---------------------------------------------------------------------------------------------
  // Copy resolution + render (design §1/§7, task 4.8). `sendNotification` ALWAYS renders from the
  // catalog at send time -- any `title`/`body` in the request body is only a legacy pass-through
  // fallback (design §7's backward-compat guarantee: pre-deploy Cloud Tasks need no migration).
  // ---------------------------------------------------------------------------------------------
  const streakCountValue =
    channel === 'streak'
      ? String(currentStreak(completions, localDay - 1))
      : channel === 'healer'
        ? // The streak count being protected/recovered by the healer -- the streak held through the
          // break day (localDay - 1), i.e. as of localDay - 2.
          String(currentStreak(completions, localDay - 2))
        : undefined;

  const context: string[] =
    channel === 'mood'
      ? [localMinuteOfDay(sendCheckedAtMillis, zone) < MOOD_EVENING_START_MINUTE ? 'afternoon' : 'evening']
      : channel === 'streak' && streakCountValue !== undefined
        ? [streakBand(Number(streakCountValue))]
        : channel === 'meditation_return' && meditationReturnDecision?.band
          ? [meditationReturnDecision.band]
          : [];

  const placeholderValues: Record<string, string> = streakCountValue !== undefined ? { streakCount: streakCountValue } : {};

  // A malformed/partially-seeded `notificationCopy` doc (e.g. missing `locales`/`placeholders`)
  // must never crash this handler before it can fall back to the legacy title/body pass-through
  // (design §7). Mirrors the try/catch pattern `answerCompassQuestion`/`syncEntitlement` already
  // use elsewhere in this file: log a structured error and fall through with `variant`/`rendered`
  // left `null`, which the legacy pass-through below already handles.
  let variant: CopyVariant | null = null;
  let rendered: CopyText | null = null;
  try {
    const catalog = await loadCopyCatalog(firestoreCatalogSource());
    const recentKeys = notificationState.variantHistory[family] ?? [];
    variant = selectVariant(catalog, family, context, recentKeys);
    rendered = variant ? renderCopy(variant, locale, placeholderValues) : null;
  } catch (err) {
    console.error(
      JSON.stringify({ event: 'notification_send_failed', uid, channel, error: String(err) }),
    );
  }

  const title = rendered?.title ?? legacyTitle;
  const body = rendered?.body ?? legacyBody;
  const variantKey = rendered ? variant?.key : undefined;

  const v2Data: Record<string, string> = {
    ...(legacyData ?? {}),
    family,
    destination: DESTINATION_BY_CHANNEL[channel],
    ctaKey: CTA_KEY_BY_CHANNEL[channel],
    locale,
    ...(variantKey ? { variantKey } : {}),
    ...(streakCountValue !== undefined ? { streakCount: streakCountValue } : {}),
    ...(channel === 'meditation_return' && meditationReturnDecision?.inactiveDays !== undefined
      ? { inactiveDays: String(meditationReturnDecision.inactiveDays) }
      : {}),
    ...(channel === 'reflection' && variant ? { questionId: variant.key } : {}),
    ...(channel === 'healer' ? { expiringToday: String(isHealerExpiringToday(completions, healerUses, localDay)) } : {}),
  };

  const tokensSnap = await db.collection(`users/${uid}/fcmTokens`).get();
  const tokens = tokensSnap.docs.map((d: QueryDocumentSnapshot) => d.id);

  const fcmClient: FcmClient = {
    async send(token, message) {
      await getMessaging().send({
        token,
        data: {
          ...(message.data ?? {}),
          channel: message.channel,
          ...(message.title ? { title: message.title } : {}),
          ...(message.body ? { body: message.body } : {}),
        },
        android: {
          priority: 'high',
          collapseKey: message.channel,
          // Admin SDK accepts milliseconds and serializes the HTTP v1 `ttl` duration string.
          ttl: message.ttl ? Number(message.ttl.slice(0, -1)) * 1000 : undefined,
        },
      });
    },
  };
  const tokenStore: TokenStore = {
    async deleteToken(forUid, token) {
      await db.doc(`users/${forUid}/fcmTokens/${token}`).delete();
    },
  };

  const results = await sendAndPrune(fcmClient, tokenStore, uid, tokens, { channel, title, body, data: v2Data, ttl });
  if (hasTransientFcmFailures(results)) {
    res.status(503).send('Transient FCM failure');
    return;
  }

  // design §2/§9: state/delivery writes + the `notification_delivered` analytics log happen ONLY
  // on a successful (non-transient) send.
  const stateUpdate: Partial<NotificationStateDoc> = {};
  if (variantKey) {
    stateUpdate.variantHistory = buildVariantHistoryUpdate(notificationState.variantHistory, family, variantKey);
  }
  if (channel === 'meditation_return' && meditationReturnDecision?.nextState) {
    stateUpdate.meditationReturn = meditationReturnDecision.nextState;
  }
  if (Object.keys(stateUpdate).length > 0) {
    await db.doc(`users/${uid}/notificationState/current`).set(stateUpdate, { merge: true });
  }

  const deliveredAtMillis = Date.now();
  const deliveryEntry: DeliveryLogEntry = {
    deliveredAtMillis,
    variantKey: variantKey ?? 'legacy-fallback',
    destination: DESTINATION_BY_CHANNEL[channel],
  };
  const nextDeliveryLog = buildDeliveryLogUpdate(deliveryLog, localDay, family, deliveryEntry);
  await db.doc(`users/${uid}/notificationDeliveries/${localDay}`).set(nextDeliveryLog, { merge: true });

  console.log(
    JSON.stringify({
      event: 'notification_delivered',
      uid,
      family,
      variantKey: variantKey ?? null,
      locale,
      destination: DESTINATION_BY_CHANNEL[channel],
    }),
  );

  res.status(200).send('OK');
});

// -------------------------------------------------------------------------------------------
// Play Billing entitlement (design.md's free-pro-subscription change, D1/D2). Pure logic lives
// in `billing.ts` (Vitest); this section is SDK wiring only, same split as the rest of this file.
// -------------------------------------------------------------------------------------------

// Deploy-time configuration -- differs per environment, same category as QUEUE_PATH/SEND_URL above.
const RTDN_PUSH_AUDIENCE = process.env.RTDN_PUSH_AUDIENCE ?? '';
const RTDN_PUSH_SERVICE_ACCOUNT = process.env.RTDN_PUSH_SERVICE_ACCOUNT ?? '';
const ANDROID_PACKAGE_NAME = process.env.ANDROID_PACKAGE_NAME ?? 'com.pirxhio.affirmity';
// JSON key for the Play Developer API service account (user-owned prerequisite, design.md Phase 0).
const PLAY_SERVICE_ACCOUNT_KEY_JSON = process.env.PLAY_SERVICE_ACCOUNT_KEY_JSON;

function entitlementDocRef(uid: string) {
  return getFirestore().doc(`users/${uid}/entitlements/current`);
}

function firestoreEntitlementStore(): EntitlementStore {
  return {
    async getLastVerifiedAt(uid) {
      const snapshot = await entitlementDocRef(uid).get();
      const value = snapshot.data()?.lastVerifiedAt;
      return typeof value === 'number' ? value : null;
    },
    async writeEntitlement(uid, doc: EntitlementDoc) {
      await entitlementDocRef(uid).set({ ...doc, updatedAt: FieldValue.serverTimestamp() });
    },
  };
}

let playApiClientPromise: Promise<PlayApiClient> | null = null;

/** Lazily-built Play Developer API client (design D1 step 5 -- the sole source of purchase
 * authority). Memoized so the OAuth client/JWT is built once per function instance, not per call. */
function playApiClient(): Promise<PlayApiClient> {
  if (!playApiClientPromise) {
    playApiClientPromise = (async () => {
      const auth = new google.auth.GoogleAuth({
        scopes: ['https://www.googleapis.com/auth/androidpublisher'],
        credentials: PLAY_SERVICE_ACCOUNT_KEY_JSON ? JSON.parse(PLAY_SERVICE_ACCOUNT_KEY_JSON) : undefined,
      });
      const androidPublisher = google.androidpublisher({ version: 'v3', auth });
      return {
        async getSubscription(packageName, purchaseToken) {
          const response = await androidPublisher.purchases.subscriptionsv2.get({
            packageName,
            token: purchaseToken,
          });
          return response.data as unknown as PlaySubscriptionV2;
        },
      };
    })();
  }
  return playApiClientPromise;
}

/** Verifies the Pub/Sub push OIDC token and extracts the claims `isTrustedPushClaims` checks
 * (design D1 step 2-3). Returns `null` on any verification failure -- `handleRtdn` maps that to 401. */
async function verifyRtdnPushClaims(authorizationHeader: string | undefined): Promise<OidcClaims | null> {
  if (!authorizationHeader?.startsWith('Bearer ')) return null;
  const idToken = authorizationHeader.slice('Bearer '.length);
  try {
    const ticket = await oauthClient.verifyIdToken({ idToken, audience: RTDN_PUSH_AUDIENCE });
    const payload = ticket.getPayload();
    if (!payload) return null;
    return {
      iss: payload.iss,
      aud: typeof payload.aud === 'string' ? payload.aud : undefined,
      email: payload.email,
      email_verified: payload.email_verified,
    };
  } catch {
    return null;
  }
}

// -------------------------------------------------------------------------------------------
// App Store (StoreKit 2) entitlement verification for iOS (`syncEntitlementIOS`). Pure logic
// lives in `appStoreBilling.ts`; this section is SDK wiring only, same split as Play billing above.
// -------------------------------------------------------------------------------------------

// Deploy-time configuration -- same category as ANDROID_PACKAGE_NAME above. Both platforms happen
// to use the same identifier string, but they're independently configurable.
const IOS_BUNDLE_ID = process.env.IOS_BUNDLE_ID ?? 'com.pirxhio.affirmity';
// App Store Connect's numeric "Apple ID" for this app. `SignedDataVerifier`'s constructor throws
// unless this is provided whenever its environment is Production -- even though
// `verifyAndDecodeTransaction` itself never reads it -- so it's a hard prerequisite for verifying
// real (non-Sandbox) purchases, not an optional nicety. It's only obtainable once an app record
// exists in App Store Connect; the iOS app has not shipped yet, so this is very likely unset in
// every environment today. Left unset, `appStoreVerifier()` below constructs Sandbox-only, and any
// Production-environment JWS from a real App Store purchase will fail verification with 401 --
// this is a deliberate "not required now" gap, not a bug, until the user supplies this value.
const IOS_APP_APPLE_ID = process.env.IOS_APP_APPLE_ID ? Number(process.env.IOS_APP_APPLE_ID) : undefined;

let appleRootCertificatesCache: Buffer[] | null = null;

/** Loads Apple's public root CA cert (non-secret, committed under `src/certs/`, downloaded from
 * https://www.apple.com/certificateauthority/ -- the one Apple's own App Store Server Library docs
 * point to for this verification path). `__dirname` resolves to `lib/` at runtime (compiled) or
 * `src/` under `vitest` -- both are one level under `functions/`, so `../src/certs/...` reaches the
 * same file either way. */
function appleRootCertificates(): Buffer[] {
  if (!appleRootCertificatesCache) {
    const certPath = join(__dirname, '..', 'src', 'certs', 'AppleRootCA-G3.cer');
    appleRootCertificatesCache = [readFileSync(certPath)];
  }
  return appleRootCertificatesCache;
}

let productionVerifierCache: SignedDataVerifier | null | undefined;
let sandboxVerifierCache: SignedDataVerifier | null = null;

/** `undefined` cache sentinel means "not yet attempted"; `null` means "attempted, unavailable"
 * (no `IOS_APP_APPLE_ID` configured -- see above). Memoized so the verifier/cert are built once
 * per function instance, same pattern as `playApiClient()`. */
function productionVerifier(): SignedDataVerifier | null {
  if (productionVerifierCache === undefined) {
    productionVerifierCache =
      IOS_APP_APPLE_ID !== undefined
        ? new SignedDataVerifier(appleRootCertificates(), true, AppStoreEnvironment.PRODUCTION, IOS_BUNDLE_ID, IOS_APP_APPLE_ID)
        : null;
  }
  return productionVerifierCache;
}

function sandboxVerifier(): SignedDataVerifier {
  if (!sandboxVerifierCache) {
    sandboxVerifierCache = new SignedDataVerifier(appleRootCertificates(), true, AppStoreEnvironment.SANDBOX, IOS_BUNDLE_ID);
  }
  return sandboxVerifierCache;
}

/**
 * Real `AppStoreVerifier`: a client's JWS can legitimately come from either the Production or
 * Sandbox App Store environment (a real purchase vs. a TestFlight/dev build), and the library only
 * supports one environment per verifier instance, so this tries Production first (when configured)
 * and falls back to Sandbox on `INVALID_ENVIRONMENT` -- the specific failure the library throws
 * when a JWS verifies cryptographically but was minted for the other environment.
 * `RETRYABLE_VERIFICATION_FAILURE` (Apple's OCSP/revocation-check endpoint unreachable) is treated
 * as transient and rethrown as-is so the caller maps it to 500; every other `VerificationException`
 * status is a genuine auth failure, wrapped as `AppStoreVerificationError` (401).
 */
function appStoreVerifier(): AppStoreVerifier {
  return {
    async verifyTransaction(signedTransaction: string) {
      const production = productionVerifier();
      if (production) {
        try {
          return await production.verifyAndDecodeTransaction(signedTransaction);
        } catch (err) {
          if (!(err instanceof VerificationException) || err.status !== VerificationStatus.INVALID_ENVIRONMENT) {
            throw mapVerificationError(err);
          }
          // Fall through to Sandbox below.
        }
      }
      try {
        return await sandboxVerifier().verifyAndDecodeTransaction(signedTransaction);
      } catch (err) {
        throw mapVerificationError(err);
      }
    },
  };
}

function mapVerificationError(err: unknown): Error {
  if (err instanceof VerificationException && err.status === VerificationStatus.RETRYABLE_VERIFICATION_FAILURE) {
    return err;
  }
  if (err instanceof VerificationException) {
    return new AppStoreVerificationError(err.message || 'Apple JWS verification failed');
  }
  return err instanceof Error ? err : new Error('unknown-error');
}

/**
 * Client-triggered App Store entitlement sync (iOS's `StoreKitPurchaseService.swift`), called right
 * after a purchase/restore completes. `firestore.rules` denies ALL client writes to
 * `users/{uid}/entitlements/current` (server-authoritative, same rule Android's `syncEntitlement`
 * already relies on), so this Cloud Function -- Admin SDK, bypasses rules -- is the only legitimate
 * path to that write for iOS. Auth follows the same trust leg as `answerCompassQuestion`: a Firebase
 * ID token in the `Authorization` header, verified via `getAuth().verifyIdToken`, and the decoded
 * token's own `uid` claim used directly as the write target -- see `appStoreBilling.ts`'s doc
 * comment for why that's the right (and deliberately simpler-than-Play's) trust model here.
 */
export const syncEntitlementIOS = onRequest(async (req, res) => {
  if (req.method !== 'POST') {
    res.status(405).send('Method Not Allowed');
    return;
  }

  const authorization = req.headers.authorization;
  if (!authorization?.startsWith('Bearer ')) {
    res.status(401).send('Unauthorized');
    return;
  }
  let uid: string;
  try {
    const decoded = await getAuth().verifyIdToken(authorization.slice('Bearer '.length));
    uid = decoded.uid;
  } catch {
    res.status(401).send('Unauthorized');
    return;
  }

  const { signedTransaction } = req.body as { signedTransaction?: unknown };
  if (typeof signedTransaction !== 'string' || signedTransaction.length === 0) {
    res.status(400).send('Missing or invalid signedTransaction');
    return;
  }

  try {
    const result = await resolveIosEntitlement(appStoreVerifier(), firestoreEntitlementStore(), uid, signedTransaction, Date.now());
    if (result.outcome === 'invalid') {
      res.status(401).send('Unauthorized');
      return;
    }
    res.status(200).json({ outcome: result.outcome });
  } catch (err) {
    res.status(500).send(err instanceof Error ? err.message : 'unknown-error');
  }
});

/**
 * Play RTDN push endpoint (design.md D1). Pub/Sub retries any non-2xx response, so the status
 * contract computed by `handleRtdn` (401 auth failure, 200 drop-or-write, 500 transient failure)
 * is forwarded verbatim.
 */
export const playRtdn = onRequest(async (req, res) => {
  if (req.method !== 'POST') {
    res.status(405).send('Method Not Allowed');
    return;
  }

  const claims = await verifyRtdnPushClaims(req.headers.authorization);
  const playApi = await playApiClient();
  const result = await handleRtdn({
    claims,
    claimPolicy: { audience: RTDN_PUSH_AUDIENCE, serviceAccountEmail: RTDN_PUSH_SERVICE_ACCOUNT },
    body: req.body,
    packageName: ANDROID_PACKAGE_NAME,
    playApi,
    store: firestoreEntitlementStore(),
    nowMillis: Date.now(),
  });

  if (result.status === 401) {
    res.status(401).send('Unauthorized');
    return;
  }
  if (result.status === 500) {
    res.status(500).send(result.reason);
    return;
  }
  res.status(200).send('OK');
});

/**
 * Client-triggered re-sync (design.md D2): called right after a purchase is acknowledged, and on
 * cold start when the client believes it should be Pro but the cached doc still says Free.
 * Authenticated with a Firebase ID token -- a different trust leg from `playRtdn`'s Pub/Sub push
 * OIDC token. The resolved uid always comes from the Play API's `externalAccountIdentifiers`
 * (D3), never from the caller's self-reported identity, so a caller can only ever trigger a
 * (harmless, idempotent) re-verification of whichever account the purchase token truly belongs to.
 */
export const syncEntitlement = onRequest(async (req, res) => {
  if (req.method !== 'POST') {
    res.status(405).send('Method Not Allowed');
    return;
  }

  const authorization = req.headers.authorization;
  if (!authorization?.startsWith('Bearer ')) {
    res.status(401).send('Unauthorized');
    return;
  }
  try {
    await getAuth().verifyIdToken(authorization.slice('Bearer '.length));
  } catch {
    res.status(401).send('Unauthorized');
    return;
  }

  const { purchaseToken } = req.body as { purchaseToken?: string };
  if (!purchaseToken) {
    res.status(400).send('Missing purchaseToken');
    return;
  }

  try {
    const playApi = await playApiClient();
    const result = await resolveEntitlement(
      playApi,
      firestoreEntitlementStore(),
      ANDROID_PACKAGE_NAME,
      purchaseToken,
      'sync',
      Date.now(),
    );
    res.status(200).json({ outcome: result.outcome });
  } catch (err) {
    res.status(500).send(err instanceof Error ? err.message : 'unknown-error');
  }
});

/**
 * Client-triggered "I answered today's Compass question" write (Notifications V2 scope-expansion
 * decision, made mid-Phase-5 apply after discovering the app had no Compass answer surface at
 * all): the ONLY writer of `users/{uid}/compassAnswers/{localDay}` (design D9). `firestore.rules`
 * denies ALL client writes to that collection outright -- a client-writable doc there would let a
 * modified client fake "answered" to silence its own Compass notifications -- so this Cloud
 * Function (Admin SDK, bypasses rules) is the only legitimate path to that write.
 *
 * Auth follows the exact same trust leg as [syncEntitlement] above: a Firebase ID token in the
 * `Authorization: Bearer` header, verified via `getAuth().verifyIdToken`. Unlike `syncEntitlement`,
 * the resolved uid IS used directly as the write target (there is no separate "which account does
 * this purchase token belong to" indirection here) -- but it still never comes from anything the
 * client asserts about itself beyond the token's own subject claim.
 *
 * `localDay` is derived server-side from the caller's own `users/{uid}/settings/preferences.
 * timeZone` (same `utcMillisToLocalEpochDay` pattern `loadActiveUserInputs` above and
 * `sendNotification` already use), never trusted from the client request body, so a caller can't
 * misrecord which local day it answered on. A caller with no timezone captured yet (never opened
 * notification settings) gets 400 -- there is no reasonable default local day to fall back to.
 */
export const answerCompassQuestion = onRequest(async (req, res) => {
  if (req.method !== 'POST') {
    res.status(405).send('Method Not Allowed');
    return;
  }

  const authorization = req.headers.authorization;
  if (!authorization?.startsWith('Bearer ')) {
    res.status(401).send('Unauthorized');
    return;
  }
  let uid: string;
  try {
    const decoded = await getAuth().verifyIdToken(authorization.slice('Bearer '.length));
    uid = decoded.uid;
  } catch {
    res.status(401).send('Unauthorized');
    return;
  }

  const { questionId } = req.body as { questionId?: unknown };
  // Bound comfortably above the longest real catalog key (33 chars, `notification-copy.v1.json`)
  // -- a bare truthy check lets a non-string value (object/number/array) pass through and get
  // stored verbatim via `.set()` below.
  if (typeof questionId !== 'string' || questionId.length === 0 || questionId.length > 100) {
    res.status(400).send('Missing or invalid questionId');
    return;
  }

  try {
    const db = getFirestore();
    const settingsDoc = await db.doc(`users/${uid}/settings/preferences`).get();
    const zone: string | undefined = settingsDoc.data()?.timeZone;
    if (!zone) {
      res.status(400).send('Missing timeZone');
      return;
    }

    const answeredAtMillis = Date.now();
    const localDay = utcMillisToLocalEpochDay(answeredAtMillis, zone);
    const doc = buildCompassAnswerDoc(localDay, questionId, answeredAtMillis);
    await db.doc(`users/${uid}/compassAnswers/${localDay}`).set(doc);
    res.status(200).json({ localDay });
  } catch (err) {
    res.status(500).send(err instanceof Error ? err.message : 'unknown-error');
  }
});
