# Design: FCM Notifications (Firebase migration stage 3 of 3)

## Technical Approach

All trigger computation moves to a new `functions/` Node/TS deploy unit. An hourly Cloud
Scheduler tick invokes one planner function; for every user whose local time has just hit the
planning hour it computes that user's local-day trigger instants (ported slot math), evaluates the
streak channel, and enqueues one Cloud Task per instant. Each fired task calls a send function that
pushes a data-only FCM message. The client's only notification responsibility becomes
`onMessageReceived` → `Notifier.notify`. Removing `NotificationScheduler` deletes the
DataStore-vs-Firestore divergence outright: after this change the only settings read path is
`DataSession.notifications`, which is `FirestoreNotificationSettingsRepository` when signed in.

## Architecture Decisions

| Decision | Choice | Rejected | Rationale |
|---|---|---|---|
| Scheduling engine | Hourly Cloud Scheduler tick; plan users whose local hour == 03 | Single fixed-UTC nightly job | Local-day boundaries span ~26h; a single UTC instant plans some users mid-day |
| Dispatch | Cloud Tasks, one task per slot, fired at the exact instant | 5–15 min polling (exploration #1) | Preserves to-the-minute random slots; no fan-out query per tick |
| Planner idempotency | Deterministic task name `{uid}-{localDay}-{channel}-{slot}` + `users/{uid}/notificationPlans/{localDay}` marker | In-memory dedupe | Cloud Tasks rejects duplicate names (`ALREADY_EXISTS` = success); marker doc only set on success so a failed user is retried next tick |
| Failure isolation | Per-user `try/catch`, failures written to the plan doc, pass never aborts | Fail whole run | Spec requirement; one malformed settings doc cannot silence every user |
| Active-user discovery | `collectionGroup('settings')` full scan per tick, filter local hour in memory | Denormalized `planHourUtc` field | Simplest correct form at current scale; revisit past ~10k users |
| Message shape | Data-only, `collapse_key = channelId`, priority high | Notification payload | Guarantees `onMessageReceived` runs (tray-only messages skip it when backgrounded) and keeps channel/i18n choices on-device |
| Token store | Subcollection `users/{uid}/fcmTokens/{token}` | Field on the settings doc | Multi-device support; doc-id-is-token makes prune a single delete |
| Timezone | `timeZone` field on the existing `settings/preferences` doc | New root doc | Planner already reads that doc; one read, no schema sprawl |
| Day rollover | Keep `DayRolloverWorker` on-device (signed-out widget correctness) **and** add a silent `day_rollover` FCM ping for signed-in users | Delete the worker | Proposal keeps an on-device widget path; the ping is the reliable trigger, the worker is the offline fallback |
| Client testability | Extract pure `FcmMessageHandler` + `NotificationPoster` interface (implemented by `Notifier`) | Test the `FirebaseMessagingService` directly | Keeps the strict-TDD test in `app/src/test` (JVM) with no Robolectric |

## Data Flow

    Cloud Scheduler (hourly)
        └─→ planNotifications ── collectionGroup('settings') ── per user (localHour==03):
                 ├─ slot math (schedule.ts) ─→ 3 tasks/channel
                 ├─ streak eval (streak.ts + dailyCompletions) ─→ 0..1 task @ 20:00 local
                 └─ write notificationPlans/{localDay}
        Cloud Tasks ──(OIDC, at instant)──→ sendNotification
                 └─ read fcmTokens ─→ FCM ─→ prune UNREGISTERED/INVALID_ARGUMENT
    Device: AffirmityMessagingService.onMessageReceived ─→ FcmMessageHandler ─→ Notifier.notify
                                                      └─(day_rollover)→ WeeklyTrackerWidget.updateAll

## File Changes

| File | Action | Description |
|---|---|---|
| `functions/package.json`, `tsconfig.json`, `vitest.config.ts` | Create | Node 20 / TS project, `npm test` = Vitest |
| `functions/src/schedule.ts` | Create | Port of `subWindow` / `nextTriggerAtMillis` (pure) |
| `functions/src/streak.ts` | Create | Port of `DailyCompletionStats.streakOf` + `shouldFireStreakAlert` |
| `functions/src/localDay.ts` | Create | IANA zone ↔ epochDay ↔ UTC instant helpers |
| `functions/src/planner.ts`, `tasks.ts`, `fcm.ts`, `index.ts` | Create | Planner pass, Cloud Tasks enqueue, FCM send + token prune, exports |
| `functions/test/{schedule,streak,planner,fcm}.test.ts` | Create | Vitest suites |
| `notifications/AffirmityMessagingService.kt` | Create | `FirebaseMessagingService`; `onMessageReceived` → handler, `onNewToken` → token repo |
| `notifications/FcmMessageHandler.kt` | Create | Pure map→action resolver (JVM-testable) |
| `data/remote/FcmTokenRepository.kt` | Create | Write/refresh `users/{uid}/fcmTokens/{token}` |
| `app/src/test/.../notifications/FcmMessageHandlerTest.kt` | Create | Strict-TDD RED first |
| `notifications/NotificationScheduler.kt`, `ReminderWorker.kt`, `ReflectionPromptWorker.kt`, `AffirmityWorkerFactory.kt`, `NotificationSchedule.kt` | Delete | Local scheduling chain retired |
| `app/src/test/.../notifications/NotificationScheduleTest.kt` | Delete | Cases migrate to `functions/test/schedule.test.ts` |
| `androidTest/.../notifications/{NotificationScheduler,ReminderWorker,ReflectionPromptWorker}InstrumentedTest.kt` | Delete | WorkManager mechanics no longer exist |
| `notifications/NotificationChannelSpec.kt` | Modify | Add `STREAK` (id `affirmity_streak_alerts`, notificationId 1003, prefsPrefix `streak`); drop `uniqueWorkName`/`workTag` |
| `data/AffirmityAppState.kt` | Modify | Drop `notificationScheduler` param + 3 call sites; add `fcmTokenRepository`, `deviceTimeZoneId` provider, timezone/token sync on sign-in; `STREAK` branch in the channel `when` |
| `data/repository/NotificationSettingsRepository.kt` + Room/Firestore impls | Modify | Add `setTimeZone(zoneId)` (no-op locally, field write remotely) |
| `data/remote/FirestorePaths.kt` | Modify | `fcmTokensCollection` / `fcmTokenDoc` |
| `AffirmityApplication.kt` | Modify | Drop `Configuration.Provider` + factory; keep channel creation (now 3 channels) |
| `AndroidManifest.xml` | Modify | `<service>` with `MESSAGING_EVENT` filter + default channel meta-data |
| `firestore.rules` | Modify | Explicit `fcmTokens` block with shape validation |
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | Modify | `firebase-messaging` via BOM 34.16.0 |
| `res/values*/strings.xml` | Modify | Streak channel name/description + notification copy |
| `app/src/test/.../data/AffirmityAppStateSwapTest.kt` | Modify | Constructor change + timezone-sync assertion |

## Interfaces / Contracts

```ts
// functions/src/schedule.ts — parity with the retired Kotlin object
export function subWindow(startMinute: number, endMinute: number, slotIndex: number, slotCount: number): [number, number];
export function slotInstant(localDay: number, zone: string, startMinute: number, endMinute: number, rng: () => number): Date;
// functions/src/streak.ts
export function streakOf(rows: Completion[], todayEpochDay: number, isDone: (r: Completion) => boolean): number;
export function shouldFireStreakAlert(rows: Completion[], todayEpochDay: number): boolean; // streak>=1 && (!affirmation || !meditation) today
```

```kotlin
interface NotificationPoster { suspend fun notify(channel: NotificationChannelSpec, title: String, body: String) }
sealed interface FcmAction { data class Post(val channel: NotificationChannelSpec, val title: String, val body: String) : FcmAction
    data object RefreshWidget : FcmAction; data object Ignore : FcmAction }
class FcmMessageHandler(private val strings: (NotificationChannelSpec) -> Pair<String, String>) {
    fun resolve(data: Map<String, String>): FcmAction   // keys: channel, title?, body?
}
```

Firestore additions: `users/{uid}/settings/preferences` gains `timeZone: string (IANA)` and
`timeZoneUpdatedAt: timestamp`. `users/{uid}/fcmTokens/{token}` = `{ token, platform: "android",
createdAt, updatedAt }`. `users/{uid}/notificationPlans/{localDay}` (server-written) =
`{ localDay, timeZone, plannedAt, slots: [...], status }`.

Rules delta (owner-only, shape-validated; Admin SDK bypasses rules, so the planner is unaffected):

```
match /users/{uid}/fcmTokens/{token} {
  allow read, delete: if request.auth.uid == uid;
  allow create, update: if request.auth.uid == uid
    && request.resource.data.token == token
    && request.resource.data.platform == 'android';
}
match /users/{uid}/notificationPlans/{day} { allow read: if request.auth.uid == uid; allow write: if false; }
```

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit (Kotlin, strict TDD, `gradlew.bat testDebugUnitTest`) | `FcmMessageHandler.resolve` → post/ignore/refresh; `Notifier` called with the right channel/title/body; unknown channel never posts | RED-first JUnit4 in `app/src/test`, fake `NotificationPoster`; no Android deps |
| Unit (Kotlin) | `FirestorePaths` token paths; `AffirmityAppStateSwapTest` timezone sync | Existing pure-path patterns |
| Unit (TS, `cd functions && npm test`, manual) | `schedule.test.ts` mirrors all 7 `NotificationScheduleTest` cases (thirds split, remainder absorption, in-window pick, degenerate, inverted-clamped, next-day roll); `streak.test.ts` mirrors `DailyCompletionStatsTest` + the 3 spec streak scenarios | Vitest, seeded RNG for determinism |
| Unit (TS) | Planner idempotency (second run for same `localDay` enqueues nothing), per-user failure isolation, token prune on `UNREGISTERED`/`INVALID_ARGUMENT` | Vitest with in-memory Firestore/Tasks fakes |
| Manual E2E | Overnight force-stopped delivery, second device, streak fire/no-fire, widget rollover | Device, after console prerequisites are confirmed |

Not wired into Gradle: the Vitest suite is run and reported manually (proposal decision 4). Strict
TDD applies to the Kotlin side only.

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, or executable-file classification boundary.
The one process-integration boundary is Cloud Tasks → `sendNotification`: it MUST be an
authenticated HTTP target (OIDC token from a dedicated service account, no public invoker), and the
function MUST reject requests without a valid caller identity. This is a design requirement for
tasks.

## Migration / Rollout

Additive schema only — no backfill. Order: (1) schema + rules + timezone/token client sync ship
first and are harmless to the current client; (2) `functions/` deploy; (3) client FCM receipt +
WorkManager removal. Kill switches: pause the Cloud Scheduler job (stops all sends), or disable the
streak evaluator alone. Users signed out get no notifications by design; the widget still rolls over
locally.

**User-owned prerequisites (outside this repo — must be confirmed done in the Firebase/GCP console
before `sdd-apply` can be validated end to end):** Blaze billing enabled; `cloudfunctions`,
`cloudscheduler`, `cloudtasks`, `cloudbuild`, `artifactregistry`, `run` APIs enabled; a Cloud Tasks
queue (e.g. `notification-dispatch`) created in the chosen region; a service account holding
`roles/cloudtasks.enqueuer` + `roles/run.invoker` for the OIDC target. Code and tests can be written
and unit-verified without these; only live delivery is blocked.

## Open Questions

- [ ] None blocking. Planning hour (local 03:00) and streak notification copy are implementation
      details settled in tasks.
