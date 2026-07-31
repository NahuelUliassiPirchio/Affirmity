# Exploration: FCM notifications (stage 3 of Firebase migration)

## Current State

This refreshes the umbrella exploration (`openspec/changes/firebase-migration/exploration.md`) for stage 3 specifically, after confirming stage 2 (`firestore-data`) is archived and live in the codebase.

### WorkManager self-rescheduling chains (unchanged since umbrella exploration)

- `NotificationScheduler` (`notifications/NotificationScheduler.kt`) splits each channel's enabled window into `SLOTS_PER_DAY = 3` independent `enqueueUniqueWork` chains (`{uniqueWorkName}_slot{N}`). `ensureScheduled()` reseeds any slot with nothing pending (called on every app relaunch); `scheduleNext(channel, slot)` rolls a fresh random trigger time via `NotificationSchedule.nextTriggerAtMillis`/`subWindow` (pure JVM-testable math, `notifications/NotificationSchedule.kt`).
- `ReminderWorker`/`ReflectionPromptWorker` (`CoroutineWorker`s) each: check `NotificationPreferences.isEnabled` (DataStore, not Firestore — see below), post via `Notifier`, then self-reschedule (`scheduler.scheduleNext(channel, slot)`) before returning `Result.success()`. If the process dies between "notify posted" and "reschedule call," the chain silently stops — this is the core Doze-fragility bug this stage exists to fix.
- `AffirmityWorkerFactory` (`notifications/AffirmityWorkerFactory.kt`) is a hand-rolled `WorkerFactory` (no DI framework) wired app-wide in `AffirmityApplication.workManagerConfiguration` — injects `AffirmationDao` (Room, direct `AffirmityDatabase.getInstance(this)`) and `NotificationPreferences` (DataStore) into the two workers. It still constructs a Room DAO directly rather than going through `AffirmityAppState`'s Room/Firestore session abstraction.
- `Notifier` (`notifications/Notifier.kt`) builds/posts the actual notification, gated on `NotificationManagerCompat.areNotificationsEnabled()`. This class is scheduling-agnostic and is the piece most likely to survive largely unchanged (its `notify()` method is exactly what an FCM message handler would call).
- `NotificationChannelSpec` still defines exactly two channels (`REMINDER`, `REFLECTION`) — no third "streak about to end" entry exists.
- `widget/DayRolloverWorker.kt` is a second, independent self-rescheduling `CoroutineWorker` (`day_rollover_worker`, fires ~60s after local midnight via `DayRolloverSchedule.delayUntilNextMidnightMillis`, re-enqueues itself in its own `doWork()`). It has the identical Doze/self-rescheduling fragility as the two notification workers, confirmed unchanged. `AffirmityWorkerFactory.createWorker()` explicitly returns `null` for it (falls through to WorkManager's default factory), so it isn't even covered by the app's own hand-rolled DI.

### NEW finding since firestore-data landed: notification scheduling never reads Firestore

`AffirmityAppState.kt`'s `rememberAffirmityAppState()` (lines ~487–533) wires:
- `DataSession.Local.notifications = RoomNotificationSettingsRepository(notificationPreferences)` (DataStore-backed)
- `DataSession.Remote.notifications = FirestoreNotificationSettingsRepository(firestore, uid)` (stage-2-added, backed by `users/{uid}/settings/preferences` doc, confirmed in `data/remote/FirestoreNotificationSettingsRepository.kt`)
- **but** `notificationScheduler = NotificationScheduler(context.applicationContext, notificationPreferences, notificationDebugLog)` is constructed directly from the raw local `NotificationPreferences` DataStore instance, **not** from either `DataSession` branch.

This means: even after stage 2's Room↔Firestore runtime swap, the actual `ReminderWorker`/`ReflectionPromptWorker` scheduling engine reads only the local on-device DataStore prefs, never the Firestore-backed settings doc a signed-in user edits through the synced session. `FirestoreNotificationSettingsRepository` exists and is wired into `DataSession.Remote` but is currently dead code from the scheduler's point of view — it only affects whatever UI reads `AffirmityAppState.reminderSettings`/`reflectionSettings` (lines ~220–345), not what workers actually check via `preferences.isEnabled(channel)` inside `ReminderWorker`/`ReflectionPromptWorker`. This is a pre-existing latent inconsistency stage 3 needs to resolve (Cloud Functions computing schedules server-side MUST read the same settings doc the user edits, i.e. Firestore, not the local DataStore copy this local scheduler currently reads).

### Firestore is confirmed as account-scoped source of truth (stage 2 recap, verified live in code)

- `data/remote/FirestoreDailyCompletionRepository.kt`: `users/{uid}/dailyCompletions/{epochDay}` docs, numeric `epochDay` field range-queried (not lexicographic doc-id ordering), `markMeditation`/`markAffirmation` do idempotent `set(..., SetOptions.merge())`.
- `data/remote/FirestoreNotificationSettingsRepository.kt`: single `users/{uid}/settings/preferences` doc, both channels' `enabled`/`startMinute`/`endMinute` stored with a `{prefsPrefix}_` key prefix mirroring the DataStore convention.
- `data/remote/FirestorePaths.kt` confirms the full per-user schema: `users/{uid}/affirmations`, `users/{uid}/dailyCompletions`, `users/{uid}/settings/preferences`, `users/{uid}/meta/migrated`.
- **No timezone field exists anywhere in this schema.** `data/DayClock.kt`'s `epochDay()` is still purely `Calendar.getInstance()`-derived (device-local, no persisted timezone), confirmed unchanged since the umbrella exploration. This directly blocks any Cloud Function from correctly evaluating "is epochDay N complete for this user" or "is local midnight approaching for this user" — the server has no reliable way to know what "today" or "about to end" means for a given `uid` without a new persisted timezone/offset field.

### Firebase dependency state (confirmed fresh, not carried over from umbrella doc)

`gradle/libs.versions.toml` + `app/build.gradle.kts`: `firebase-bom` (34.16.0), `firebase-auth`, `firebase-firestore` are present and applied (stage 1/2 additions). **`firebase-messaging` and any Cloud Functions client library are absent.** `AndroidManifest.xml` still declares only `INTERNET` and `POST_NOTIFICATIONS` permissions, no `<service>` for a `FirebaseMessagingService`, no `com.google.firebase.messaging.default_notification_channel_id` meta-data. Repo-wide grep for `messaging|FCM|firebase-functions|cloudscheduler` across `*.kt/*.toml/*.kts/*.xml` returns zero matches, and no `functions/` directory exists. This is a fully greenfield FCM/Functions integration — nothing to build on beyond the Auth/Firestore client SDKs already in place.

### Tests (confirmed still present, still WorkManager-coupled)

All four files named in the umbrella exploration exist unchanged:
- `androidTest/.../notifications/NotificationSchedulerInstrumentedTest.kt` — uses `WorkManagerTestInitHelper.initializeTestWorkManager`, asserts exactly one pending `WorkInfo` per slot after calling `ensureScheduled()` twice (idempotent-reseed regression test for slot chains).
- `androidTest/.../notifications/ReminderWorkerInstrumentedTest.kt`, `ReflectionPromptWorkerInstrumentedTest.kt` — confirmed present at the same paths; per umbrella exploration these use `TestListenableWorkerBuilder`.
- `androidTest/.../data/AffirmityAppStateInstrumentedTest.kt` — confirmed present; covers `DailyCompletionStats` streak derivation among other things (Room-based currently).
- `androidTest/.../widget/WeeklyTrackerWidgetContentTest.kt` also exists but was not covered as "WorkManager-coupled" in the umbrella doc; it exercises Glance content, likely orthogonal to `DayRolloverWorker`'s scheduling mechanics specifically.

All of this coverage exercises WorkManager mechanics (`getWorkInfosForUniqueWork`, `TestListenableWorkerBuilder`) that become meaningless once scheduling moves server-side — confirmed, not something that changed since the umbrella exploration.

### Streak-derivation logic relevant to "streak about to end"

`data/DailyCompletionStats.kt` is a pure, dependency-free object: `streakOf(rows, todayEpochDay, isDone)` walks backwards day-by-day from `todayEpochDay` while `isDone` holds. This logic is trivially portable to a Cloud Function (Node/TS) reading `users/{uid}/dailyCompletions` directly from Firestore — the algorithm itself is not the hard part; knowing the correct `todayEpochDay` for a given user server-side (the missing-timezone problem above) is.

## Affected Areas

- `notifications/NotificationScheduler.kt`, `ReminderWorker.kt`, `ReflectionPromptWorker.kt`, `AffirmityWorkerFactory.kt`, `NotificationSchedule.kt` — entire self-rescheduling chain and its scheduling math to be replaced by FCM message receipt + local notification post; `NotificationSchedule`'s random-slot math needs porting to the Cloud Functions side (or an equivalent server-side algorithm).
- `notifications/Notifier.kt` — survives largely as-is; becomes the thing an incoming FCM message's handler calls to post the local notification.
- `notifications/NotificationChannelSpec.kt` — needs a third entry for "streak about to end" (new `channelId`, `notificationId`, string resources); `uniqueWorkName`/`workTag`/`prefsPrefix` fields tied to the WorkManager model need re-evaluation once scheduling is server-side (prefsPrefix likely survives for the Firestore settings doc key).
- `widget/DayRolloverWorker.kt`, `DayRolloverSchedule.kt` — same self-rescheduling fragility; in scope for the same reliability fix, but the on-device Glance widget update itself must remain on-device — only the "trigger" needs to stop being self-rescheduled WorkManager.
- `data/AffirmityAppState.kt` — must fix the confirmed scheduler/Firestore-settings mismatch (currently constructs `NotificationScheduler` from local `NotificationPreferences` DataStore, bypassing `FirestoreNotificationSettingsRepository`); FCM token registration/refresh needs a home here or in a new dedicated class.
- `AffirmityApplication.kt` — `Configuration.Provider`/`AffirmityWorkerFactory` wiring shrinks or is removed entirely once no notification `CoroutineWorker`s remain locally scheduled; likely gains FCM init (`FirebaseMessaging.getInstance()`, notification channel creation logic stays since channels are still local Android constructs).
- `AndroidManifest.xml` — needs a new `<service>` entry for a `FirebaseMessagingService` subclass; possibly `com.google.firebase.messaging.default_notification_channel_id` meta-data.
- `data/remote/FirestorePaths.kt`, `FirestoreNotificationSettingsRepository.kt` — need an FCM-token field/subcollection (e.g. `users/{uid}/fcmTokens/{token}` or a token field on the settings doc) and a persisted per-user timezone/UTC-offset field to make "streak about to end" and any local-time-aware scheduling computable server-side.
- `gradle/libs.versions.toml`, `app/build.gradle.kts` — needs `firebase-messaging` added to the existing `firebase-bom` platform block; no Cloud Functions client dependency is needed on the Android side (Functions are pure server-side, invoked via Cloud Scheduler/Firestore triggers, not called from the app).
- New, not-yet-existing: a `functions/` Node/TS Cloud Functions project — confirmed still fully absent — needs: (a) a nightly/periodic "planner" scheduled function reading each active user's Firestore settings doc and computing/dispatching notification sends, (b) FCM send logic, (c) a new streak-evaluation function reading `dailyCompletions` + the new timezone field.
- `androidTest/.../notifications/NotificationSchedulerInstrumentedTest.kt`, `ReminderWorkerInstrumentedTest.kt`, `ReflectionPromptWorkerInstrumentedTest.kt` — confirmed still present and still WorkManager-coupled; to be retired/replaced with FCM-message-handling unit tests (JVM, testing the `FirebaseMessagingService.onMessageReceived` → `Notifier.notify` path) plus a separate Node/TS test suite for the Cloud Functions planner/streak logic.
- `androidTest/.../data/AffirmityAppStateInstrumentedTest.kt` — needs review for any assertions coupled to `NotificationScheduler`/local `NotificationPreferences` wiring that changes once the scheduler-Firestore mismatch above is fixed.

## Approaches

### 1. Polling Cloud Scheduler (frequent tick + Firestore query)

A single Cloud Scheduler cron job (e.g. every 5–15 minutes) triggers a Cloud Function that queries all users whose precomputed next-trigger timestamp (stored per-user in Firestore) falls within the current tick window, and sends the FCM message directly for any hits.

- **Pros:** simplest implementation — one job, no per-user dynamic scheduling infrastructure; easy to reason about and debug (just re-run the tick).
- **Cons:** granularity is bounded by tick frequency (5–15 min jitter vs. the current to-the-minute random trigger), scales linearly with active-user count per tick (a Firestore query fanning out over all users every few minutes even when nothing is due), doesn't naturally reproduce the existing "3 random slots per window" precision.
- **Effort:** Low–Medium.

### 2. Nightly planner + per-user Cloud Tasks dispatch (recommended)

A single daily Cloud Scheduler job (fixed UTC time, or per-timezone-bucket if timezone data exists) triggers a "planner" Cloud Function that, for each active user with a channel enabled, ports `NotificationSchedule.subWindow`/`nextTriggerAtMillis`'s random-slot math to compute that day's exact trigger timestamps, then enqueues one Cloud Task per slot scheduled to fire at that exact time. Each Cloud Task's target function sends the FCM message at fire time.

- **Pros:** reproduces the existing 3-random-slot behavior faithfully (same math, just moved server-side); no continuous polling — Cloud Tasks fire exactly once at the scheduled instant; scales per-user rather than per-tick; natural place to also compute the "streak about to end" trigger for that user's day, once a timezone field exists.
- **Cons:** requires standing up Cloud Tasks (a new GCP primitive beyond Cloud Scheduler/Functions already implied by the umbrella exploration) — more infrastructure surface than approach 1; planner function must run reliably and handle partial failures (a crashed planner run means that user gets zero notifications that day, need retry/idempotency).
- **Effort:** Medium–High.

### 3. Client-observed Firestore + local AlarmManager (no FCM for scheduling, FCM only as a wake signal)

Keep trigger-time computation on-device (port nothing server-side), but replace `WorkManager`'s self-rescheduling chain with `AlarmManager.setExactAndAllowWhileIdle` scheduled from the app itself when it's foregrounded, using FCM purely as an app-wake mechanism if the process was killed.

- **Pros:** smallest server-side footprint — no Cloud Functions/Scheduler/Tasks needed at all for the reminder/reflection channels; least new infrastructure.
- **Cons:** does not fix the actual problem — `AlarmManager` exact alarms are also subject to Doze deferral/restriction on many OEMs and API levels, and this approach still requires the app to be launched at least once per day to reschedule, which is the same class of fragility as the current self-rescheduling WorkManager chain; does not deliver "streak about to end" at all (that inherently needs server-side evaluation since the app may never open on a day the user is at risk of breaking their streak) — explicitly contradicts the task's requirement to design that notification.
- **Effort:** Low, but does not meet the stated goal.

## Recommendation

Approach 2 (nightly planner + per-user Cloud Tasks). It is the only option that both fixes the underlying Doze-self-rescheduling fragility (this stage's core motivation) and makes "streak about to end" computable server-side with the same per-user precision the existing reminder/reflection channels have. Approach 1 is an acceptable **fallback/interim** if Cloud Tasks setup proves too large a single PR slice — it can ship first (coarser timing) and be refined into approach 2 later without changing the client-side FCM-receipt code at all, since the app-side contract (`FirebaseMessagingService.onMessageReceived` → `Notifier.notify`) is identical either way.

Before either server-side approach can compute "streak about to end" or an accurate per-user trigger window, two data-model decisions from the umbrella exploration remain **unresolved and now concretely blocking**:
1. A persisted per-user timezone/UTC-offset field must be added to `users/{uid}/settings/preferences` (or similar) — without it, no Cloud Function can determine "today" or "near local midnight" for that user.
2. `NotificationChannelSpec` needs its third channel defined (id, notification id, string resources, prefs key prefix) before any spec work can proceed.

Also fix the confirmed scheduler/Firestore mismatch in `AffirmityAppState.kt` as part of this stage regardless of approach chosen — it's a pre-existing bug that would otherwise carry forward into the FCM design (the settings a Cloud Function planner reads must be the same ones the signed-in-user UI actually edits).

## Risks

- **"Streak about to end" still has zero designed trigger semantics** — confirmed unchanged from the umbrella exploration: no existing channel, no existing server-observable "about to miss" signal. Needs an explicit product decision (e.g. "X hours before local midnight if today's affirmation/meditation isn't done") before spec work, and now concretely depends on the new timezone field above.
- **No per-user timezone is persisted anywhere in Firestore** — confirmed via fresh read of `FirestorePaths.kt` and `DayClock.kt`; this is a harder blocker than the umbrella exploration implied, because it affects not just "streak about to end" but any server-side computation of "today" for a given user at all.
- **`NotificationScheduler` currently reads local DataStore prefs, not the Firestore-backed settings repository that already exists** (`FirestoreNotificationSettingsRepository`, wired into `DataSession.Remote` but unused by the scheduler) — a newly confirmed pre-existing inconsistency that must be resolved as part of this stage or a server-side planner will read settings the user doesn't think they're editing.
- **`DayRolloverWorker` has the identical Doze/self-rescheduling failure mode** but lives in `widget/`, not `notifications/` — still easy to scope out of an "FCM notifications" change by name alone; the widget's on-device Glance update itself must stay client-side even if the "new day" trigger signal moves server-side.
- **Zero FCM/Cloud Functions/Cloud Tasks infrastructure exists** — confirmed via fresh dependency and manifest checks (no `firebase-messaging`, no `FirebaseMessagingService`, no `functions/` directory, no repo-wide FCM/Functions/Scheduler references). This is fully greenfield work on top of the Auth/Firestore foundation stages 1–2 already built.
- **All WorkManager-coupled androidTest files (`NotificationSchedulerInstrumentedTest`, `ReminderWorkerInstrumentedTest`, `ReflectionPromptWorkerInstrumentedTest`) are confirmed still present and will become dead weight** — replacement coverage needs both a JVM-testable FCM-receipt path (client) and a new Node/TS test suite (Cloud Functions planner/streak logic), which is a new test story, not a drop-in replacement.
- **Approach 2's Cloud Tasks dependency is new infrastructure beyond what the umbrella exploration scoped** (it only mentioned Cloud Scheduler + Cloud Functions) — this should be flagged explicitly in the proposal since it changes the GCP services list and associated IAM/cost surface.

## Ready for Proposal

Yes, with two open decisions the proposal must resolve up front (both now confirmed as concrete blockers, not just design-taste choices):
1. Approach 1 (polling) vs. Approach 2 (nightly planner + Cloud Tasks) vs. explicitly phasing 1 first and 2 later — recommend 2, or 1-then-2 if Cloud Tasks is judged too large for one PR slice.
2. The "streak about to end" trigger condition and the per-user timezone field's exact shape/location in the `users/{uid}` schema — this is genuine product+schema design that should land in `design.md`, not be improvised during `sdd-tasks`/`sdd-apply`.

---

**Status:** partial (exploration complete; two open decisions above need proposal-level confirmation before design)
**Next recommended:** sdd-propose
