# Exploration: Migrate persistence and notifications to Firebase

## Current State

### Data model (Room + DataStore)

- `AffirmationEntity` (`app/src/main/java/com/pirxhio/affirmity/data/local/AffirmationEntity.kt`): `id (PK, UUID string)`, `title`, `subtitle`, `backgroundType ("color"|"image")`, `backgroundValue`. For images, `backgroundValue` is a **local filesystem path** (`AffirmationImageStore` downloads/imports and stores the file under app-private storage) — this is local-only state, not a URL, and would need to become a Firebase Storage URL (or re-architected upload flow) before Firestore sync is viable, not just a field copy.
- `DailyCompletionEntity` (`daily_completion` table): PK `epochDay` (device-local day number from `DayClock`, not a timestamp), `meditationDone`, `affirmationDone`. This is the *only* source of truth for streaks — `DailyCompletionStats.toWeeklyStreak`/`streakOf` derive both the weekly grid and the "current streak" count live from these rows; there is no separate cached streak counter anywhere. `epochDay` is derived from `Calendar.getInstance()` (device timezone) — moving this to a multi-device/cloud model requires deciding a canonical day boundary (server day vs. device-local day) since epoch-day arithmetic assumes a single device's clock/timezone.
- `NotificationPreferences` (DataStore `notification_prefs`): per-channel (`REMINDER`, `REFLECTION`) `enabled` bool + `startMinute`/`endMinute` window, keyed by string prefix, no user identity at all today (single local user).
- `TrackerPreferences` (DataStore `tracker_prefs`): `affirmationsViewedToday` (epochDay+count, used only to detect "hit the daily goal of 2 views" — resets daily, arguably ok as ephemeral/local-only) and `meditationDurationSeconds` (last picked duration, a genuine per-user setting worth syncing).
- `NotificationDebugLog` (DataStore `notification_debug_log`, ring buffer of last 100 entries): explicitly **debug-only, device-local**, should NOT sync to Firestore — becomes moot once scheduling moves server-side, but a comparable debug surface (FCM delivery/Cloud Function log) will likely be wanted post-migration.
- Room DB is a single unnamed/unscoped `affirmity.db`, no per-user partitioning anywhere (`AffirmityDatabase.getInstance(context)` is a bare singleton, no user id in the schema or file name). Firestore migration means introducing a user-id dimension into every collection design from scratch.

### Notification architecture (WorkManager self-rescheduling)

- `NotificationScheduler` splits each channel's time window into 3 daily "slots" (`SLOTS_PER_DAY = 3`), each running as its own independent `enqueueUniqueWork` chain (`{uniqueWorkName}_slot{N}`), so one dropped slot doesn't kill the others.
- `ReminderWorker`/`ReflectionPromptWorker` (`CoroutineWorker`s) each: check `NotificationPreferences.isEnabled`, post via `Notifier`, then call `scheduler.scheduleNext(channel, slot)` on themselves before finishing — this self-rescheduling chain is what fails under Doze/battery optimization (WorkManager work can be delayed/deferred indefinitely by the OS, and if the process is killed between "notification posted" and "reschedule call," the chain silently dies).
- `AffirmityWorkerFactory` is a hand-rolled `WorkerFactory` (no DI framework) injecting `AffirmationDao`/`NotificationPreferences` into the two workers — set as the app-wide factory in `AffirmityApplication.workManagerConfiguration`.
- `Notifier` builds/posts the actual Android notification, gated on `NotificationManagerCompat.areNotificationsEnabled()`.
- **No FCM/push code exists anywhere** — `AndroidManifest.xml` declares only `INTERNET` and `POST_NOTIFICATIONS` permissions, no `FirebaseMessagingService`.
- **"Streak about to end" notification logic does not exist today, anywhere.** Confirmed by reading `DailyCompletionStats`, `ReminderWorker`, `ReflectionPromptWorker`, `NotificationChannelSpec` (only two channels: `REMINDER`, `REFLECTION`) and the widget code. This is new product logic, not a migration of existing behavior.
- Streak *state* exists but is Room-only and widget-adjacent: `DailyCompletionDao`/`DailyCompletionEntity` plus `WeeklyTrackerWidget` (Glance widget) reads the same `AffirmityDatabase.dailyCompletionDao()` directly (not through `AffirmityAppState`). `DayRolloverWorker` is a **second, separate self-rescheduling WorkManager chain** (`day_rollover_worker`, fires ~60s after local midnight, re-enqueues itself) with the exact same Doze/self-rescheduling fragility as the notification workers — easy to overlook since it's in `widget/`, not `notifications/`.

### Firebase/network/auth dependencies

Confirmed via `gradle/libs.versions.toml`, `app/build.gradle.kts`, top-level `build.gradle.kts`, and a repo-wide grep for `firebase`/`google-services`: **nothing is set up**. No Firebase BOM, no `google-services` Gradle plugin, no `google-services.json`, no Auth/Firestore/Messaging/Functions client libraries, no networking library beyond a plain download call inside `AffirmationImageStore`. This is a genuinely greenfield integration, not a partial one.

### Package/module structure

Single `:app` module, no multi-module split. Layering by feature-ish package: `data/` (app state + `data/local/` for Room/DataStore), `notifications/`, `widget/`, `ui/{screens}/`. No DI framework — dependencies are constructed by hand in `rememberAffirmityAppState()` and `AffirmityApplication`. A new `auth/` package (parallel to `notifications/`/`widget/`) and a Firestore-backed replacement/wrapper for `data/local/` would fit the existing convention; Cloud Functions code lives outside this repo/module entirely (separate `functions/` Node/TS project is the norm).

`AffirmityAppState` is the single composition point every screen depends on — the most deeply embedded coupling point: any Firestore/Auth migration has to slot in at this exact seam (adding `authRepository`, replacing `affirmationDao`/`dailyCompletionDao`/`trackerPreferences`/`notificationPreferences` with Firestore-backed equivalents) to avoid rewriting every screen composable.

### Tests

All 4 relevant `androidTest` files (`NotificationSchedulerInstrumentedTest`, `ReminderWorkerInstrumentedTest`, `ReflectionPromptWorkerInstrumentedTest`, `AffirmityAppStateInstrumentedTest`) depend on `WorkManagerTestInitHelper`/`TestListenableWorkerBuilder` and assert on WorkManager mechanics. **All of this coverage is WorkManager-specific and will be dead code once local scheduling is replaced** — needs a like-for-like replacement testing FCM message handling / Cloud Function trigger logic (likely a separate Node/TS test suite, not JVM/Espresso). `AffirmityAppStateInstrumentedTest` also tests `DailyCompletionStats` streak-derivation correctness (Room-based) and needs a Firestore-backed equivalent to preserve that regression coverage.

`openspec/config.yaml` sets `apply.tdd: true` and `test_command: "gradlew.bat testDebugUnitTest"` (JVM unit tests only — androidTest/Espresso needs a connected device/emulator).

## Affected Areas

- `data/local/AffirmationEntity.kt`, `AffirmationDao.kt`, `AffirmityDatabase.kt` — Room schema to mirror/replace with Firestore per-user collection; image `backgroundValue` local-path scheme needs a cloud-storage equivalent.
- `data/local/DailyCompletionEntity.kt`, `DailyCompletionDao.kt`, `data/DailyCompletionStats.kt`, `data/DayClock.kt` — streak source of truth; `DayClock`'s device-local epoch-day model needs a cross-device day-boundary decision before Firestore sync.
- `data/local/NotificationPreferences.kt`, `TrackerPreferences.kt`, `NotificationDebugLog.kt` (DataStore) — settings to move to Firestore (user-scoped) vs. keep local (debug log).
- `notifications/*` (`NotificationScheduler.kt`, `ReminderWorker.kt`, `ReflectionPromptWorker.kt`, `Notifier.kt`, `AffirmityWorkerFactory.kt`, `NotificationSchedule.kt`, `NotificationChannelSpec.kt`) — the entire self-rescheduling chain to be replaced by FCM receipt + local notification post; `Notifier`/`NotificationChannelSpec` likely survive largely as-is, scheduling math moves server-side (Cloud Scheduler/Functions).
- `widget/DayRolloverWorker.kt`, `DayRolloverSchedule.kt` — a second, easy-to-miss self-rescheduling WorkManager chain with the same Doze fragility; in scope for the same reliability fix.
- `data/AffirmityAppState.kt` — the single composition root every screen depends on; new `authRepository`/Firestore-backed repositories need to slot in here without breaking the screen-facing API shape.
- `AffirmityApplication.kt` — WorkManager `Configuration.Provider`/`AffirmityWorkerFactory` wiring shrinks to just local-notification-posting; likely gains Firebase init.
- `AndroidManifest.xml` — needs `FirebaseMessagingService` registration.
- `gradle/libs.versions.toml`, `app/build.gradle.kts`, top-level `build.gradle.kts` — need BOM + `google-services` plugin + Auth/Firestore/Messaging client libs added from scratch, plus a `google-services.json` (secrets/config handling not yet established).
- `androidTest/.../notifications/*InstrumentedTest.kt` (3 files) and `AffirmityAppStateInstrumentedTest.kt` — WorkManager-coupled tests to retire/replace.
- New, not-yet-existing: a Cloud Functions project (sibling `functions/` directory) for FCM dispatch + Cloud Scheduler triggers + "streak about to end" evaluation — net-new server-side code with its own test story.

## Approaches

### 1. Full replace (single migration)

Rip out Room/DataStore/WorkManager notification chains, ship Firebase-only in one coordinated cutover.

- **Pros:** no dual-write/sync complexity; smallest final codebase.
- **Cons:** violates the 800-line PR review budget by a wide margin (spans DAOs, app state, all notification classes, widget rollover, build config, and a new server-side project); "streak about to end" logic designed from scratch inside the same change; no incremental rollback point; loses all androidTest coverage in one shot.
- **Effort:** High.

### 2. Staged migration (recommended)

Auth first → Firestore data layer (read-through/cutover) → FCM/notifications last, as three sequenced SDD changes.

- **Pros:** each stage independently shippable/verifiable; "streak about to end" gets its own focused spec/design pass; WorkManager tests retired stage-by-stage; natural rollback points per stage.
- **Cons:** temporary dual-source-of-truth risk (Room + Firestore) during the middle stage — this codebase's `TrackerPreferences` already documents a prior "dual-source-of-truth drift" bug class, so this needs explicit guardrails; more coordination overhead across stages.
- **Effort:** Medium per stage, High in aggregate, each stage individually reviewable.

### 3. FCM-only first, defer Auth/Firestore

Replace just the self-rescheduling WorkManager chains with FCM + Cloud Scheduler + Cloud Functions; keep Room/DataStore local, app stays anonymous.

- **Pros:** directly fixes the stated Doze pain point with the smallest footprint; no per-user data-model redesign yet.
- **Cons:** doesn't deliver "public launch"/multi-device/auth requirements; Cloud Functions computing "streak about to end" still need some server-visible per-device streak state — either a partial Firestore mirror (muddying "defer Firestore") or a separate lightweight store that gets thrown away once full Firestore lands.
- **Effort:** Medium, but creates likely rework later.

## Recommendation

Approach 2 (staged migration): **(a)** Firebase project setup + Auth (Google/Apple sign-in), **(b)** Firestore data layer for affirmations/settings/streak state (explicit read-through-then-cutover plan to avoid the dual-source-of-truth trap this codebase has already been bitten by once), **(c)** FCM + Cloud Functions + Cloud Scheduler replacing WorkManager notification/rollover chains, including new "streak about to end" design. Stage ordering matters: Auth before Firestore (need a user id to scope collections), Firestore before FCM (Cloud Functions computing "streak about to end" need to read the same Firestore state the app writes).

## Risks

- **"Streak about to end" is undesigned today** — no existing trigger condition, no existing third notification channel, no existing server-observable signal for "user is about to miss today's habit." Needs real product/design decisions (what "about to end" means in hours/timezone terms, per-user local-time awareness from a server-side Cloud Scheduler) before it can be spec'd.
- **Device-local day boundary (`DayClock.epochDay`) vs. server/cloud model** — today's streak math implicitly assumes one device's timezone. Moving day-completion state to Firestore (multi-device, Cloud Functions with no fixed timezone) requires an explicit canonical day-boundary decision — skipping this risks re-introducing the kind of correctness bug (`AffirmityAppStateInstrumentedTest`'s "broken streak" regression test) this codebase already fixed once.
- **Image `backgroundValue` is a local file path, not a URL** — Firestore-syncing affirmations requires either Firebase Storage upload/URL rewrite or accepting that images stay device-local and only metadata syncs (product decision).
- **`DayRolloverWorker` (widget) has the identical Doze/self-rescheduling failure mode** but lives in `widget/`, not `notifications/` — easy to scope out of the "notifications" migration by name alone and leave un-fixed.
- **Zero existing Firebase/network/DI infrastructure** — fully greenfield integration (no BOM, no `google-services` plugin, no secrets-handling convention, no DI framework); underestimating this as "just swap the backend" risks scope creep into build-tooling and secrets-management decisions.
- **All 3 WorkManager-coupled androidTest files become dead weight** and their replacement likely needs a different test stack entirely (Node/TS for Cloud Functions, not JVM/Espresso) — coverage parity is a new test story, not a drop-in replacement.
- **No multi-module or DI convention exists** to lean on for a growing surface area (Auth, Firestore repos, Cloud Functions client calls) — proposal should explicitly decide whether to introduce one (e.g., Hilt) or continue the hand-wired composition-root pattern at larger scale.

## Open Question for Proposal

Confirm with the user: single large change vs. the staged 3-part sequence above — this choice changes the shape of every downstream artifact (spec/design/tasks).

---

**Status:** partial (exploration complete; scope confirmation needed before proposal)
**Next recommended:** sdd-propose (after confirming single-change vs. staged-sequence scope with the user)
