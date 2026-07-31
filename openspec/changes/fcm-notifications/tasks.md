# Tasks: FCM Notifications (Firebase migration stage 3 of 3)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~1800–2200 (functions/ ~800 new, Android additive ~220 new, deletions ~700, modifications ~200) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR1 schema/rules → PR2 functions/ → PR3 Android additive → PR4 Android removal+bugfix |
| Delivery strategy | auto-chain |
| Chain strategy | stacked-to-main |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Firestore schema/rules foundation | PR1 | `gradlew.bat testDebugUnitTest --tests "*FirestorePaths*"` | N/A — schema/rules only, no runtime path yet | Revert `FirestorePaths.kt`/`firestore.rules` diff; additive, nothing reads it yet |
| 2 | `functions/` planner+tasks+fcm+streak | PR2 | `cd functions && npm test` | N/A — deploy unit not yet wired to a live Scheduler trigger | Delete `functions/` dir; independent deploy unit, no app coupling |
| 3 | Android FCM receipt path (additive) | PR3 | `gradlew.bat testDebugUnitTest --tests "*FcmMessageHandler*"` | Manual: install APK, send test FCM message via console, confirm notification posts | Revert new files + manifest `<service>`; old scheduling still active in parallel |
| 4 | Android removal + bugfix + streak wiring | PR4 | `gradlew.bat testDebugUnitTest` | Manual: sign in, edit reminder window, confirm Firestore doc updates | Revert branch restores WorkManager chain byte-for-byte (per proposal rollback plan) |

## Phase 1: Firestore Schema & Rules (PR1)

- [x] 1.1 `data/remote/FirestorePaths.kt`: add `fcmTokensCollection`/`fcmTokenDoc`
- [x] 1.2 `NotificationSettingsRepository.kt` (+Room no-op, +Firestore impl): add `setTimeZone(zoneId)`
- [x] 1.3 `FirestoreNotificationSettingsRepository.kt`: write `timeZone`/`timeZoneUpdatedAt` on `settings/preferences`
- [x] 1.4 `firestore.rules`: add `fcmTokens` (owner-only, shape-validated) + `notificationPlans` (read-only) blocks

## Phase 2: functions/ Node/TS Project (PR2)

- [x] 2.1 Scaffold `functions/package.json`, `tsconfig.json`, `vitest.config.ts` (Node 20, `npm test`=vitest)
- [x] 2.2 `functions/test/schedule.test.ts` (7 cases ported from `NotificationScheduleTest`) → `functions/src/schedule.ts` (`subWindow`, `slotInstant`)
- [x] 2.3 `functions/src/localDay.ts`: zone/epochDay/UTC instant helpers
- [x] 2.4 `functions/test/streak.test.ts` (streakOf + 3 spec scenarios) → `functions/src/streak.ts` (`streakOf`, `shouldFireStreakAlert`)
- [x] 2.5 `functions/test/planner.test.ts` (idempotent replan, per-user failure isolation) → `functions/src/planner.ts`
- [x] 2.6 `functions/src/tasks.ts`: Cloud Tasks enqueue, deterministic name `{uid}-{localDay}-{channel}-{slot}`
- [x] 2.7 `functions/test/fcm.test.ts` (prune on `UNREGISTERED`/`INVALID_ARGUMENT`) → `functions/src/fcm.ts` (send + prune)
- [x] 2.8 `functions/src/index.ts`: export `planNotifications` (Scheduler-triggered), `sendNotification` (Tasks-triggered, OIDC-checked)
- [x] 2.9 Run `cd functions && npm test`; all suites green (manual report, no Gradle wiring)

## Phase 3: Android FCM Receipt Path (PR3)

- [x] 3.1 RED `app/src/test/.../notifications/FcmMessageHandlerTest.kt`: resolve→Post/Ignore/RefreshWidget; unknown channel never posts
- [x] 3.2 GREEN `notifications/FcmMessageHandler.kt` + `NotificationPoster` interface; `Notifier` implements it
- [x] 3.3 `data/remote/FcmTokenRepository.kt`: write/refresh `users/{uid}/fcmTokens/{token}`
- [x] 3.4 `notifications/AffirmityMessagingService.kt`: `onMessageReceived`→handler, `onNewToken`→repo, `day_rollover`→widget update
- [x] 3.5 `gradle/libs.versions.toml` + `app/build.gradle.kts`: add `firebase-messaging` (BOM 34.16.0)
- [x] 3.6 `AndroidManifest.xml`: register `<service>` (`MESSAGING_EVENT` filter + default channel meta-data)

## Phase 4: Android Removal + Bugfix + Streak Wiring (PR4)

- [x] 4.1 Delete `NotificationScheduler.kt`, `ReminderWorker.kt`, `ReflectionPromptWorker.kt`, `AffirmityWorkerFactory.kt`, `NotificationSchedule.kt`
- [x] 4.2 Delete `NotificationScheduleTest.kt` + 3 WorkManager `androidTest` files
- [x] 4.3 `notifications/NotificationChannelSpec.kt`: add `STREAK` (id `affirmity_streak_alerts`, notificationId 1003, prefsPrefix `streak`); drop `uniqueWorkName`/`workTag`
- [x] 4.4 `data/AffirmityAppState.kt`: drop `notificationScheduler` param + 3 call sites; signed-in scheduling reads `FirestoreNotificationSettingsRepository` only; add `STREAK` branch to channel `when`
- [x] 4.5 `data/AffirmityAppState.kt`: wire `fcmTokenRepository` + `deviceTimeZoneId` provider; sync timezone+token on sign-in and launch
- [x] 4.6 `AffirmityApplication.kt`: drop `Configuration.Provider`/`WorkerFactory` wiring; keep 3-channel creation
- [x] 4.7 RED/GREEN `app/src/test/.../data/AffirmityAppStateSwapTest.kt`: constructor change + timezone-sync assertion
- [x] 4.8 `res/values*/strings.xml`: streak channel name/description + notification copy

## Phase 5: Verification (gates PR4 merge)

- [ ] 5.1 `gradlew.bat testDebugUnitTest` green; no WorkManager notification worker left in diff
- [ ] 5.2 `gradlew.bat assembleDebug` builds
- [ ] 5.3 `cd functions && npm test` green (manual report)
- [ ] 5.4 Manual E2E (needs console prerequisites): 3 daily slots/user-timezone; settings-edit reflected next plan; overnight force-stop delivery; streak fire/no-fire; token refresh on 2nd device; widget rollover at local midnight
