/**
 * Cloud Functions entry points: `planNotifications` (Cloud Scheduler-triggered) and
 * `sendNotification` (Cloud Tasks-triggered, OIDC-checked). Deployment/live wiring is out of
 * scope for this PR (no Firebase project/credentials in this environment, per design.md's
 * "User-owned prerequisites") -- this file is written to a reasonable production shape but is
 * intentionally not unit-tested; the pure logic it wires (schedule/localDay/streak/planner/
 * tasks/fcm) is covered by the Vitest suites next to it.
 */

import { initializeApp, getApps } from 'firebase-admin/app';
import { getFirestore, FieldValue, type QueryDocumentSnapshot } from 'firebase-admin/firestore';
import { getMessaging } from 'firebase-admin/messaging';
import { onSchedule } from 'firebase-functions/v2/scheduler';
import { onRequest } from 'firebase-functions/v2/https';
import { CloudTasksClient as GoogleCloudTasksClient } from '@google-cloud/tasks';
import { OAuth2Client } from 'google-auth-library';

import { localHourInZone, utcMillisToLocalEpochDay } from './localDay';
import { planAllUsers, type PlanStore, type PlanResult, type TaskEnqueuer, type UserPlanInput } from './planner';
import { taskName } from './tasks';
import { sendAndPrune, type FcmClient, type TokenStore } from './fcm';

export * from './schedule';
export * from './localDay';
export * from './streak';
export * from './planner';
export * from './tasks';
export * from './fcm';

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
        reminderSegments: Array.isArray(data.reminder_segments) ? data.reminder_segments : [],
        reflectionSegments: Array.isArray(data.reflection_segments) ? data.reflection_segments : [],
        moodSegments: Array.isArray(data.mood_segments) ? data.mood_segments : [],
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

  const { uid, channel, localDay, title, body } = req.body as {
    uid: string;
    channel: string;
    localDay?: number;
    title?: string;
    body?: string;
  };
  if (!uid || !channel) {
    res.status(400).send('Missing uid/channel');
    return;
  }

  const db = getFirestore();

  // The mood check-in is redundant once the user already logged today's mood between planning
  // time (PLANNING_LOCAL_HOUR, early morning) and this task actually firing tonight.
  if (channel === 'mood' && typeof localDay === 'number') {
    const moodDoc = await db.doc(`users/${uid}/dailyMoods/${localDay}`).get();
    if (moodDoc.exists) {
      res.status(200).send('Skipped: mood already logged');
      return;
    }
  }

  const tokensSnap = await db.collection(`users/${uid}/fcmTokens`).get();
  const tokens = tokensSnap.docs.map((d: QueryDocumentSnapshot) => d.id);

  const fcmClient: FcmClient = {
    async send(token, message) {
      await getMessaging().send({
        token,
        data: {
          channel: message.channel,
          ...(message.title ? { title: message.title } : {}),
          ...(message.body ? { body: message.body } : {}),
        },
        android: { priority: 'high', collapseKey: message.channel },
      });
    },
  };
  const tokenStore: TokenStore = {
    async deleteToken(forUid, token) {
      await db.doc(`users/${forUid}/fcmTokens/${token}`).delete();
    },
  };

  await sendAndPrune(fcmClient, tokenStore, uid, tokens, { channel, title, body });
  res.status(200).send('OK');
});
