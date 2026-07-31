# Proposal: FCM Notifications (Firebase migration stage 3 of 3)

## Intent

Today every notification depends on a self-rescheduling WorkManager chain: `ReminderWorker`/`ReflectionPromptWorker`/`DayRolloverWorker` each re-enqueue themselves inside their own `doWork()`. Under Doze or OEM battery restriction the chain silently stops and the user simply stops being reminded — with no signal that anything broke. Stage 3 moves trigger computation off the device: the server decides when to notify, the client only receives and posts. That also unlocks the one notification the current architecture cannot deliver at all — "your streak is about to end" — because it must fire on a day the app is never opened.

## Scope

### In Scope

- **Server-driven scheduling (settled)**: nightly Cloud Scheduler job → planner Cloud Function that ports `NotificationSchedule.subWindow`/`nextTriggerAtMillis` (random 3-slot-per-window math) to Node/TS, computes each active user's trigger instants for their local day, and enqueues one Cloud Task per slot. Each fired task sends an FCM message.
- **New `functions/` Node/TS project** (net new): planner, FCM send, streak evaluation.
- **Client receipt path**: `firebase-messaging` in `gradle/libs.versions.toml`, a `FirebaseMessagingService` subclass + `<service>` manifest registration, `onMessageReceived` → existing `Notifier.notify()` (survives largely as-is).
- **Retire local scheduling**: `NotificationScheduler`, `ReminderWorker`, `ReflectionPromptWorker`, `AffirmityWorkerFactory`; `DayRolloverWorker`'s self-rescheduling chain becomes a server-driven trigger while the Glance `updateAll` stays on-device.
- **Timezone capture (settled)**: client auto-detects `TimeZone.getDefault().id` (IANA id) at sign-in / first launch, persists it to `users/{uid}/settings/preferences`, re-syncs on change. No picker UI.
- **Firestore schema additions**: FCM token storage under `users/{uid}` plus the timezone field, on top of the stage-2 `FirestorePaths.kt` schema.
- **Bug fix**: `AffirmityAppState.kt` constructs the scheduler from the local DataStore `NotificationPreferences`, never from `FirestoreNotificationSettingsRepository`. Signed-in scheduling MUST read the same Firestore settings doc the user edits — otherwise the planner schedules from settings the user never touched.
- **Third channel**: `NotificationChannelSpec` gains a "streak about to end" entry; a Cloud Function reads `dailyCompletions` + the persisted timezone and ports `DailyCompletionStats.streakOf` logic to decide per-user whether to fire that day.
- **Test story**: retire `NotificationSchedulerInstrumentedTest`, `ReminderWorkerInstrumentedTest`, `ReflectionPromptWorkerInstrumentedTest`; add a JVM test for `onMessageReceived` → `Notifier.notify`; add a **separate Node/TS suite** for planner + streak logic.

### Out of Scope

- Polling-tick scheduling (exploration approach 1) and local `AlarmManager` (approach 3) — **rejected, not deferred**.
- Manual timezone picker UI; per-timezone Scheduler bucketing beyond what the nightly planner needs.
- Notification content personalization, quiet hours, snooze, per-notification analytics.
- Any change to `DayClock`/`epochDay` client semantics beyond persisting the zone id.
- Room/DataStore removal; signed-out server-side scheduling (see below).

### Signed-out users — explicit position

Signed-out users get **no** notifications after this stage. Local WorkManager scheduling for signed-out users is **deprecated and removed**, not preserved: keeping it would require maintaining both the fragile chain this stage exists to delete and its Doze failure mode. Reminders, reflection prompts and streak alerts become a signed-in capability. The day-rollover widget refresh keeps an on-device path so the widget stays correct while signed out.

## Capabilities

### New Capabilities

- `push-notifications`: server-computed notification scheduling, FCM delivery, client receipt/post, and the streak-about-to-end trigger.

### Modified Capabilities

- `data-sync`: `users/{uid}` schema gains an FCM token store and a persisted IANA timezone field; notification settings become the single source the server planner reads.

## Approach

The client keeps exactly one notification responsibility: receive a message and post it via `Notifier`. Everything time-related moves server-side. A daily Cloud Scheduler tick runs the planner once; the planner reads each active user's Firestore settings doc + timezone, runs the ported slot math for that user's local day, and enqueues Cloud Tasks at exact instants — reproducing today's 3-random-slots-per-window behavior faithfully rather than approximating it with a polling window. The streak evaluator runs in the same planner pass, reusing the already-loaded timezone and `dailyCompletions`. Client contract (`onMessageReceived` → `Notifier.notify`) is deliberately infrastructure-agnostic, so the dispatch mechanism can change later without touching the app.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `functions/` | New | Node/TS planner, FCM send, streak evaluator + its own test suite |
| `notifications/NotificationScheduler.kt`, `ReminderWorker.kt`, `ReflectionPromptWorker.kt`, `AffirmityWorkerFactory.kt` | Removed | Local scheduling chain retired |
| `notifications/NotificationSchedule.kt` | Removed/Ported | Math moves to Node/TS |
| `notifications/Notifier.kt` | Unchanged (mostly) | Called from the FCM service |
| `notifications/NotificationChannelSpec.kt` | Modified | Third channel; WorkManager-tied fields dropped |
| `notifications/` (new FCM service) | New | `FirebaseMessagingService` subclass + token refresh |
| `widget/DayRolloverWorker.kt`, `DayRolloverSchedule.kt` | Modified | Trigger becomes server-driven; Glance update stays local |
| `data/AffirmityAppState.kt` | Modified | Firestore-settings bug fix, timezone sync, token registration |
| `AffirmityApplication.kt` | Modified | `WorkerFactory` wiring shrinks; channel creation stays |
| `AndroidManifest.xml` | Modified | `<service>` + default notification channel meta-data |
| `data/remote/FirestorePaths.kt`, `FirestoreNotificationSettingsRepository.kt` | Modified | Token store + timezone field |
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | Modified | `firebase-messaging` via existing BOM 34.16.0 (minSdk 24 OK) |
| `firestore.rules` | Modified | Rules for the token store |
| 3 WorkManager `androidTest` files | Removed | Replaced by JVM FCM test + Node/TS suite |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Planner run fails ⇒ a user gets zero notifications that day | Med | Idempotent per-user planning keyed by uid+localDay; retry on the Scheduler job; per-user failures isolated, never abort the whole pass |
| Cloud Tasks is new GCP surface (IAM, quota, cost) beyond stages 1–2 | Med | Named explicitly; queue config + IAM documented in design; cost is per-task and bounded by active users × 3–4 |
| Node/TS tests fall outside `gradlew.bat testDebugUnitTest` and the session's Strict TDD command | High | **Proposal-level decision required** (see question round); design must name the Functions test command and whether Strict TDD applies to it |
| Stale/invalid FCM tokens silently drop notifications | Med | Delete tokens on `UNREGISTERED`/`INVALID_ARGUMENT` send errors; refresh on `onNewToken` |
| Signed-out users lose notifications entirely | High (by design) | Stated position above; needs user confirmation |
| Timezone drift between capture and planning (travel, DST) | Low–Med | Re-sync zone id on every launch; planner reads the zone at plan time, not at send time |
| Delivery latency/dedup: FCM is best-effort, not exactly-once | Med | Collapse key + per-slot notification id so a duplicate replaces rather than stacks |
| Removing `AffirmityWorkerFactory` breaks unrelated worker wiring | Low | Only two workers used it; `DayRolloverWorker` already fell through to the default factory |

## Rollback Plan

1. Revert the feature branch: the WorkManager chain, its workers, factory and tests return intact — the client is byte-for-byte pre-change.
2. Disable server-side sending independently: pause the Cloud Scheduler job. No messages are sent, no client change needed, no crash — the app simply receives nothing.
3. Partial rollback: keep the client FCM path and revert only the streak channel by disabling the streak evaluator in the planner.
4. Schema additions (token store, timezone field) are additive and readable by the reverted client; nothing needs deleting.
5. `functions/` is a separate deploy unit — rolling back the app does not require redeploying Functions and vice versa.

## Dependencies

- Stage 1 (`firebase-auth`) and stage 2 (`firestore-data`) merged and archived — supply `uid` and the account-scoped settings/completions schema.
- User console prerequisites: Cloud Functions, Cloud Scheduler and Cloud Tasks enabled on the existing Firebase/GCP project; billing enabled (Blaze) — Functions/Tasks require it.
- `firebase-messaging` via the existing Firebase BOM 34.16.0 — minSdk 24 compatible.
- `POST_NOTIFICATIONS` runtime permission already declared and handled.

## Success Criteria

- [ ] `gradlew.bat assembleDebug` and `gradlew.bat testDebugUnitTest` pass; no WorkManager notification worker remains in the diff.
- [ ] A signed-in user with reminders enabled receives 3 reminder notifications per day at random instants inside their configured window, in their own timezone.
- [ ] Editing reminder settings in the app changes what the planner schedules the next day (proves the Firestore-settings bug is fixed).
- [ ] Force-stopping the app and leaving the device idle overnight still yields next-day notifications.
- [ ] "Streak about to end" fires only for users with a live streak and an incomplete current local day, and does not fire once the day is completed.
- [ ] FCM token is written on sign-in and refreshed via `onNewToken`; a second device on the same account receives notifications.
- [ ] The Glance widget still rolls over at local midnight.
- [ ] Node/TS suite covers planner slot math (parity with the retired `NotificationSchedule` cases) and streak evaluation.

## Proposal question round (resolved)

All five decisions are now settled and final:

1. **Server-driven scheduling**: nightly planner + Cloud Tasks (exploration approaches 1 and 3 rejected).
2. **Timezone capture**: auto-detected IANA `TimeZone.getDefault().id`, persisted to the settings doc, no picker UI.
3. **Streak-about-to-end trigger condition**: fires once at 20:00 user-local time when the user has a streak ≥ 1 and **either** affirmation or meditation (not necessarily both) is still unmarked for the current local day — the earlier, more conservative trigger.
4. **Cloud Functions testing strategy**: a separate `npm test` (Vitest/Jest) suite inside `functions/`, run and reported manually. Strict TDD for this session applies only to the Kotlin side (`gradlew.bat testDebugUnitTest`); the Functions suite is not wired into a Gradle task.
5. **Signed-out users**: confirmed — they get no notifications after this stage. Local scheduling is removed entirely, not kept in a degraded form, consistent with stages 1–2 already excluding signed-out users from account-scoped behavior.
