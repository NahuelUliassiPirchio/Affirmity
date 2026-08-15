package com.pirxhio.affirmity.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.credentials.CredentialManager
import com.pirxhio.affirmity.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.access.AdUnlockRecord
import com.pirxhio.affirmity.auth.AuthError
import com.pirxhio.affirmity.auth.AuthException
import com.pirxhio.affirmity.auth.AuthProviderId
import com.pirxhio.affirmity.auth.AuthRepository
import com.pirxhio.affirmity.auth.AuthState
import com.pirxhio.affirmity.auth.FirebaseAuthRepository
import com.pirxhio.affirmity.auth.GoogleIdAuthProvider
import com.pirxhio.affirmity.auth.SignInCancelledException
import com.pirxhio.affirmity.data.local.AffirmationEntity
import com.pirxhio.affirmity.data.local.AffirmationGroupPreferences
import com.pirxhio.affirmity.data.local.AffirmationImageStore
import com.pirxhio.affirmity.data.local.AffirmityDatabase
import com.pirxhio.affirmity.data.local.ChannelSettings
import com.pirxhio.affirmity.data.local.DailyMoodEntity
import com.pirxhio.affirmity.data.local.DailyViewCount
import com.pirxhio.affirmity.data.local.DaySegment
import com.pirxhio.affirmity.data.local.GroupSelectionPreferences
import com.pirxhio.affirmity.data.local.NotificationDebugLog
import com.pirxhio.affirmity.data.local.NotificationLogEntry
import com.pirxhio.affirmity.data.local.NotificationPreferences
import com.pirxhio.affirmity.data.local.OnboardingPreferences
import com.pirxhio.affirmity.data.local.PERSONALIZADAS_GROUP_ID
import com.pirxhio.affirmity.data.local.QuietHoursSettings
import com.pirxhio.affirmity.data.local.TrackerPreferences
import com.pirxhio.affirmity.data.remote.FcmTokenRepository
import com.pirxhio.affirmity.data.remote.FirestoreAdUnlockRepository
import com.pirxhio.affirmity.data.remote.FirestoreAffirmationRepository
import com.pirxhio.affirmity.data.remote.FirestoreDailyCompletionRepository
import com.pirxhio.affirmity.data.remote.FirestoreDailyMoodRepository
import com.pirxhio.affirmity.data.remote.FirestoreEntitlementRepository
import com.pirxhio.affirmity.data.remote.FirestoreMeditationPreferencesRepository
import com.pirxhio.affirmity.data.remote.FirestoreMigrator
import com.pirxhio.affirmity.data.remote.FirestoreNotificationSettingsRepository
import com.pirxhio.affirmity.data.remote.FirestoreOnboardingRepository
import com.pirxhio.affirmity.data.remote.FirestoreStreakHealerRepository
import com.pirxhio.affirmity.data.remote.MigrationSnapshot
import com.pirxhio.affirmity.data.repository.AdUnlockRepository
import com.pirxhio.affirmity.data.repository.DataSession
import com.pirxhio.affirmity.data.repository.RoomAdUnlockRepository
import com.pirxhio.affirmity.data.repository.RoomAffirmationRepository
import com.pirxhio.affirmity.data.repository.RoomDailyCompletionRepository
import com.pirxhio.affirmity.data.repository.RoomDailyMoodRepository
import com.pirxhio.affirmity.data.repository.RoomMeditationPreferencesRepository
import com.pirxhio.affirmity.data.repository.RoomNotificationSettingsRepository
import com.pirxhio.affirmity.data.repository.RoomStreakHealerRepository
import com.pirxhio.affirmity.notifications.NotificationChannelSpec
import com.pirxhio.affirmity.notifications.Notifier
import com.pirxhio.affirmity.ui.groups.AffirmationGroupAccess
import com.pirxhio.affirmity.ui.groups.defaultAffirmationGroups
import com.pirxhio.affirmity.ui.groups.selectableAffirmationGroups
import com.pirxhio.affirmity.widget.WeeklyTrackerWidget
import androidx.glance.appwidget.updateAll
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Background for an affirmation card: a solid color, or a locally-cached downloaded image. */
sealed class AffirmationBackground {
    data class Color(val value: String) : AffirmationBackground()
    data class Image(val localPath: String) : AffirmationBackground()
}

data class Affirmation(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val subtitle: String,
    val background: AffirmationBackground,
    val groupId: String = PERSONALIZADAS_GROUP_ID,
)

/** Rolling last-7-days completion flags (oldest first, today last) for a habit tracker.
 * [healedDays] holds the same-indexed offsets that were saved by the streak healer instead of
 * actually completed — rendered as a mending heart instead of the usual dot/empty circle. */
data class WeeklyStreak(
    val completedDays: List<Boolean>,
    val streakDays: Int,
    val dayLabels: List<String> = List(7) { "" },
    val healedDays: Set<Int> = emptySet(),
)

/** Solid-color background, or the placeholder tint shown behind an image while it decodes. */
fun Affirmation.backgroundColor(): Color =
    when (val bg = background) {
        is AffirmationBackground.Color -> runCatching {
            Color(android.graphics.Color.parseColor(bg.value))
        }.getOrDefault(Color(0xFF00696F))

        is AffirmationBackground.Image -> Color(0xFF00696F)
    }

private fun AffirmationEntity.toAffirmation(): Affirmation = Affirmation(
    id = id,
    title = title,
    subtitle = subtitle,
    background = if (backgroundType == "image") {
        AffirmationBackground.Image(backgroundValue)
    } else {
        AffirmationBackground.Color(backgroundValue)
    },
    groupId = groupId,
)

private fun Affirmation.toEntity(): AffirmationEntity = AffirmationEntity(
    id = id,
    title = title,
    subtitle = subtitle,
    backgroundType = when (background) {
        is AffirmationBackground.Image -> "image"
        is AffirmationBackground.Color -> "color"
    },
    backgroundValue = when (val bg = background) {
        is AffirmationBackground.Image -> bg.localPath
        is AffirmationBackground.Color -> bg.value
    },
    // User-created affirmations always land in `personalizadas`, regardless of the caller's
    // [Affirmation.groupId] (spec: personalizadas Always-On; user-authored content is out of the
    // thematic-group scope for this change).
    groupId = PERSONALIZADAS_GROUP_ID,
)

/**
 * Pure resolution of the committed group-id selection, extracted so it is testable without
 * Android/DataStore (design §4, §6). [persisted] is `null` on the very first-ever launch (no
 * selection ever saved). Unknown ids (e.g. a group removed in a later release) are dropped.
 * `personalizadas` is always force-included.
 */
fun resolveSelectedGroupIds(
    persisted: Set<String>?,
    knownIds: Set<String>,
    defaultThematicIds: Set<String>,
): Set<String> {
    val resolved = persisted?.filter { it in knownIds }?.toSet() ?: defaultThematicIds
    return resolved + PERSONALIZADAS_GROUP_ID
}

/** Default for tests/previews that don't care about group selection: never emits a persisted
 * value, so callers always resolve to the first-launch default. */
private object NoOpGroupSelectionPreferences : GroupSelectionPreferences {
    override fun observeSelectedGroupIds(): kotlinx.coroutines.flow.Flow<Set<String>?> =
        kotlinx.coroutines.flow.flowOf(null)
    override suspend fun saveSelectedGroupIds(ids: Set<String>) = Unit
}

/**
 * Shared in-memory state for the whole app, backed by Room (affirmations) and DataStore
 * (trackers) — see README "Decisions". Screens read plain [Affirmation]/[WeeklyStreak] state;
 * this class owns translating that to/from the persisted shapes.
 */
class AffirmityAppState(
    private val scope: CoroutineScope,
    private val local: DataSession.Local,
    private val remoteSessionFactory: (uid: String) -> DataSession.Remote,
    private val migrator: FirestoreMigrator,
    private val trackerPreferences: TrackerPreferences,
    private val onboardingPreferences: OnboardingPreferences,
    private val imageStore: AffirmationImageStore,
    private val notificationDebugLog: NotificationDebugLog,
    private val notifier: Notifier,
    private val widgetUpdater: WidgetUpdater,
    private val authRepository: AuthRepository,
    private val fcmTokenRepository: FcmTokenRepository,
    private val onboardingRepository: FirestoreOnboardingRepository,
    /** 7-item Sun..Sat weekday-letter array, resolved from `R.array.weekday_letters` by the caller
     * (composable-only `stringResource`s can't be called from this class's coroutines) — see
     * [rememberAffirmityAppState] (D6). Defaults to the original Spanish glyphs so existing unit
     * tests that build this class directly don't need to know about locale resolution. */
    private val dayLetters: List<String> = listOf("D", "L", "M", "M", "J", "V", "S"),
    private val deviceTimeZoneId: () -> String = { TimeZone.getDefault().id },
    private val useRemoteSession: Boolean = true,
    private val groupPreferences: GroupSelectionPreferences = NoOpGroupSelectionPreferences,
    /** Every known selectable group id, resolved in [rememberAffirmityAppState] from
     * `selectableAffirmationGroups()` so this class never imports `ui.groups` (design D9). */
    private val knownGroupIds: Set<String> = setOf(PERSONALIZADAS_GROUP_ID),
    /** First-launch default thematic selection (unlocked thematic groups), also resolved by the
     * caller for the same D9 reason. */
    private val defaultThematicGroupIds: Set<String> = emptySet(),
    /** Every group id that requires Pro entitlement (`PREMIUM`/`AD_SUPPORTED` access, excluding
     * `alwaysSelected`), resolved by the caller for the same D9 reason. Consumed by the
     * downgrade-auto-deselect collector ([deselectLockedGroups] call site). */
    private val proOnlyGroupIds: Set<String> = emptySet(),
) {
    val affirmations = mutableStateListOf<Affirmation>()

    /** Group ids the user has committed. Null until DataStore's first read resolves; the UI shows
     * nothing group-dependent until then. Always contains [PERSONALIZADAS_GROUP_ID] once resolved. */
    var selectedGroupIds = mutableStateOf<Set<String>?>(null)
        private set

    /** Pending (uncommitted) selection the sheet mutates while open. Seeded from [selectedGroupIds]
     * when it first resolves. */
    var draftGroupIds = mutableStateOf(setOf(PERSONALIZADAS_GROUP_ID))
        private set

    private var draftInitialized = false

    /** True when [draftGroupIds] contains at least one thematic group, OR `personalizadas` alone
     * is selected but already has at least one affirmation of its own — an empty `personalizadas`
     * can never satisfy the invariant by itself, since selecting it alone would otherwise commit
     * to a guaranteed-empty feed. */
    val isDraftSelectionValid: Boolean
        get() = draftGroupIds.value.any { id -> id != PERSONALIZADAS_GROUP_ID } ||
            affirmations.any { it.groupId == PERSONALIZADAS_GROUP_ID }

    /** The feed's list: affirmations whose groupId is in the committed selection. NEVER used by
     * ProgressScreen (that keeps reading [affirmations] unfiltered). Falls back to the full list
     * while [selectedGroupIds] is still null. */
    val filteredAffirmations: List<Affirmation>
        get() = selectedGroupIds.value?.let { ids -> affirmations.filter { it.groupId in ids } }
            ?: affirmations

    /** Provider-neutral sign-in state; see `auth/AuthState.kt`. Settings-only, never gates a screen. */
    var authState = mutableStateOf<AuthState>(AuthState.SignedOut)
        private set

    /** Set on a recoverable sign-in failure; `null` on cancellation (not an error) or success. */
    var authError = mutableStateOf<AuthError?>(null)
        private set

    /** Set when [addAffirmationWithImage] fails to download; cleared on the next add attempt. */
    var addImageError = mutableStateOf<String?>(null)
        private set

    /** Set by [importAffirmationsFromJson]; cleared on the next import attempt. */
    var importAffirmationsError = mutableStateOf<String?>(null)
        private set

    var affirmationsStreak = mutableStateOf(WeeklyStreak(completedDays = List(7) { false }, streakDays = 0))
        private set

    var meditationStreak = mutableStateOf(WeeklyStreak(completedDays = List(7) { false }, streakDays = 0))
        private set

    /** General-streak + healer state (spec's `general-streak`/`streak-healer` domains), derived by
     * [StreakHealerStats.evaluate] from completions and the healer-use event log combined. */
    var streakHealer = mutableStateOf(
        StreakHealerState(
            generalStreakDays = 0,
            isTodayDone = false,
            healerHeld = false,
            healedDays = emptySet(),
            activation = HealerActivation.Unavailable,
        )
    )
        private set

    /** True right after [streakHealer]'s `healerHeld` flips from false to true — drives the
     * one-time [com.pirxhio.affirmity.ui.healer.StreakHealerGrantedScreen]. Never set on the first
     * emission of a (re)started healer flow, so a cold start or sign-in/out session swap that
     * happens to load an already-held healer doesn't replay the celebration. */
    var healerJustGranted = mutableStateOf(false)
        private set

    /** Dismisses [healerJustGranted] once the user has seen the grant screen. */
    fun acknowledgeHealerGrant() {
        healerJustGranted.value = false
    }

    private var healerFlowInitialized = false
        private set

    /** Current Free/Pro gating tier, resolved from the live entitlement repository (design.md
     * D5/D8). Read by [rememberAffirmityAppState]'s callers to drive `GroupAccessPolicy`. */
    var entitlementTier = mutableStateOf(AccessTier.FREE)
        private set

    /** True right after a live entitlement transition from Pro to Free is observed (design.md D8,
     * spec's "Explicit in-app lapse notice"). Never set on the first emission of a (re)started
     * entitlement flow, so a cold start or sign-in/out session swap that happens to load an
     * already-Free state doesn't fire a spurious notice. */
    var proLapseNotice = mutableStateOf(false)
        private set

    /** Dismisses [proLapseNotice] once the user has seen the snackbar (design.md D8). */
    fun acknowledgeProLapse() {
        proLapseNotice.value = false
    }

    private var entitlementFlowInitialized = false

    /** Rolling [STREAK_LOOKBACK_DAYS]-day window of mood check-ins, oldest first — the calendar
     * and "Resumen" stats derive everything else (average/distribution/trend) from this list. */
    var moodEntries = mutableStateListOf<DailyMoodEntity>()
        private set

    /** Null until DataStore finishes its first read; the screen falls back to its own default. */
    var meditationDurationSeconds = mutableStateOf<Int?>(null)
        private set

    var reminderSettings = mutableStateOf(ChannelSettings(enabled = false, segments = emptySet()))
        private set

    var reflectionSettings = mutableStateOf(ChannelSettings(enabled = false, segments = emptySet()))
        private set

    var moodSettings = mutableStateOf(ChannelSettings(enabled = false, segments = emptySet()))
        private set

    var quietHoursSettings = mutableStateOf(QuietHoursSettings(enabled = false, startMinute = 1380, endMinute = 420))
        private set

    var notificationDebugEntries = mutableStateListOf<NotificationLogEntry>()
        private set

    /** Set when a migration attempt fails on sign-in; the session stays [DataSession.Local] and
     * the user keeps a fully working offline app. Cleared on the next successful swap attempt. */
    var syncError = mutableStateOf<String?>(null)
        private set

    /** Null until DataStore finishes its first read; [MainActivity] holds off showing onboarding
     * or the main app until this resolves. */
    var hasCompletedOnboarding = mutableStateOf<Boolean?>(null)
        private set

    private var affirmationsViewedToday = DailyViewCount(epochDay = -1L, count = 0)

    /**
     * The single source of truth for which store is active. See design.md's "The swap moment":
     * `transformLatest` cancels its own previous body, and every `flatMapLatest` collector below
     * cancels its in-flight Room subscription the instant a new [DataSession] is emitted — no
     * stale Room collector survives a sign-in/sign-out swap. Writers suspend in [ready] instead of
     * racing the swap (single-writer is a type-level property, not a convention).
     */
    private val session: StateFlow<DataSession> = authRepository.authState
        .map { (it as? AuthState.SignedIn)?.uid }
        .distinctUntilChanged()
        .transformLatest { uid ->
            if (uid == null || !useRemoteSession) {
                emit(local)
                return@transformLatest
            }
            emit(DataSession.Migrating(uid, local))
            try {
                migrator.ensureMigrated(migrationSnapshotFor(uid))
                syncError.value = null
                val remote = remoteSessionFactory(uid)
                // EC-1/Q3 reconciliation: replay every local durable ad-unlock into the remote
                // repository on EVERY promotion to Remote (not only the one-time migration), so
                // sign-out -> consume a trial locally -> sign-in-again still merges. The local
                // Room copy is never deleted (design §10).
                replayDurableAdUnlocks(local.adUnlocks.getDurableUnlocks(), remote.adUnlocks)
                emit(remote)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                syncError.value = failure.message
                emit(local)
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, local)

    /** Suspends until the session leaves [DataSession.Migrating] (design.md's `ready()`), so no
     * write can land in the wrong store while a migration is in flight. */
    private suspend fun ready(): DataSession = session.first { it !is DataSession.Migrating }

    /** One-time snapshot of the Room/DataStore state for [uid], taken right before migration
     * (design.md's "The swap moment" step 4). Always reads through [local] — never [session] —
     * since this runs while the session is still [DataSession.Migrating]. */
    private suspend fun migrationSnapshotFor(uid: String): MigrationSnapshot {
        val today = DayClock.epochDay()
        return MigrationSnapshot(
            uid = uid,
            affirmations = local.affirmations.observeAll().first(),
            completions = local.completions.getRange(today - STREAK_LOOKBACK_DAYS, today + 6),
            moods = local.moods.getRange(today - STREAK_LOOKBACK_DAYS, today),
            healerUses = local.healerUses.getRange(healerStartEpochDay(today), today),
            meditationDurationSeconds = local.meditation.observeMeditationDurationSeconds().first(),
            notificationSettings = mapOf(
                NotificationChannelSpec.REMINDER to local.notifications.observe(NotificationChannelSpec.REMINDER).first(),
                NotificationChannelSpec.REFLECTION to local.notifications.observe(NotificationChannelSpec.REFLECTION).first(),
                NotificationChannelSpec.MOOD to local.notifications.observe(NotificationChannelSpec.MOOD).first(),
            ),
            quietHours = local.notifications.observeQuietHours().first(),
            migratedAt = System.currentTimeMillis(),
        )
    }

    /** EC-1/Q3 reconciliation: an additive union, never an overwrite (design §10). Idempotent —
     * [AdUnlockRepository.grantDurableUnlock] is create-if-absent, so replaying an
     * already-present [AdUnlockRecord] costs at most one denied write and never mutates the
     * remote record. Extracted as its own function so this replay step is independently testable
     * and readable at the call site. */
    private suspend fun replayDurableAdUnlocks(
        localRecords: List<AdUnlockRecord>,
        remoteAdUnlocks: AdUnlockRepository,
    ) {
        localRecords.forEach { remoteAdUnlocks.grantDurableUnlock(it) }
    }

    init {
        scope.launch {
            session.flatMapLatest { it.affirmations.observeAll() }
                .catch { error -> Log.e(TAG, "affirmations flow failed", error) }
                .collect { entities ->
                    affirmations.clear()
                    affirmations.addAll(entities.map { it.toAffirmation() })
                }
        }
        scope.launch {
            val today = DayClock.epochDay()
            val windowStart = DayClock.rollingWindowStartEpochDay()
            val dayLabels = DayClock.rollingWindowDayLetters(dayLetters)
            val healerStart = healerStartEpochDay(today)
            // The healer-use flow is combined *inside* this flatMapLatest (design.md's "Combine
            // both flows inside the existing flatMapLatest" decision) rather than in a parallel
            // collector, so both subscriptions share the same swap-cancellation lifecycle: no
            // stale Room/Firestore collector for either flow can survive a sign-in/sign-out swap.
            session.flatMapLatest { s ->
                healerFlowInitialized = false
                combine(
                    s.completions.observeRange(windowStart - STREAK_LOOKBACK_DAYS, windowStart + 6),
                    s.healerUses.observeRange(healerStart, today),
                ) { completionRows, healerRows -> completionRows to healerRows }
            }.catch { error -> Log.e(TAG, "completions/healer flow failed", error) }
                .collect { (rows, healerRows) ->
                val newHealerState = StreakHealerStats.evaluate(
                    rows = rows,
                    uses = healerRows,
                    todayEpochDay = today,
                    startEpochDay = healerStart,
                    streakStartEpochDay = StreakHealerStats.rawStreakStartEpochDay(today),
                )
                val healedIndices = (0 until 7)
                    .filter { offset -> (windowStart + offset) in newHealerState.healedDays }
                    .toSet()
                affirmationsStreak.value = DailyCompletionStats.toWeeklyStreak(
                    rows = rows,
                    weekStartEpochDay = windowStart,
                    todayEpochDay = today,
                    isDone = { it.affirmationDone },
                ).copy(dayLabels = dayLabels, healedDays = healedIndices)
                meditationStreak.value = DailyCompletionStats.toWeeklyStreak(
                    rows = rows,
                    weekStartEpochDay = windowStart,
                    todayEpochDay = today,
                    isDone = { it.meditationDone },
                ).copy(dayLabels = dayLabels, healedDays = healedIndices)
                if (healerFlowInitialized && newHealerState.healerHeld && !streakHealer.value.healerHeld) {
                    healerJustGranted.value = true
                }
                healerFlowInitialized = true
                streakHealer.value = newHealerState
            }
        }
        scope.launch {
            val today = DayClock.epochDay()
            session.flatMapLatest { it.moods.observeRange(today - STREAK_LOOKBACK_DAYS, today) }
                .catch { error -> Log.e(TAG, "moods flow failed", error) }
                .collect { rows ->
                    moodEntries.clear()
                    moodEntries.addAll(rows)
                }
        }
        scope.launch {
            trackerPreferences.observeAffirmationsViewedToday().collect { viewed ->
                affirmationsViewedToday = viewed
            }
        }
        scope.launch {
            session.flatMapLatest { it.meditation.observeMeditationDurationSeconds() }
                .catch { error -> Log.e(TAG, "meditation duration flow failed", error) }
                .collect { seconds -> meditationDurationSeconds.value = seconds }
        }
        scope.launch {
            session.flatMapLatest { it.notifications.observe(NotificationChannelSpec.REMINDER) }
                .catch { error -> Log.e(TAG, "reminder settings flow failed", error) }
                .collect { reminderSettings.value = it }
        }
        scope.launch {
            session.flatMapLatest { it.notifications.observe(NotificationChannelSpec.REFLECTION) }
                .catch { error -> Log.e(TAG, "reflection settings flow failed", error) }
                .collect { reflectionSettings.value = it }
        }
        scope.launch {
            session.flatMapLatest { it.notifications.observe(NotificationChannelSpec.MOOD) }
                .catch { error -> Log.e(TAG, "mood settings flow failed", error) }
                .collect { moodSettings.value = it }
        }
        scope.launch {
            session.flatMapLatest { it.notifications.observeQuietHours() }
                .catch { error -> Log.e(TAG, "quiet hours settings flow failed", error) }
                .collect { quietHoursSettings.value = it }
        }
        scope.launch {
            // Signed-in FCM token registration + IANA timezone sync (design.md's "Timezone"
            // decision + spec's "Token refresh is synced"): the server planner needs both to
            // compute this user's local-day trigger instants and to know where to send.
            //
            // Deliberately observes [session] itself rather than independently deriving uid from
            // [authRepository.authState] and snapshotting session state after the fact: the latter
            // raced [session]'s own transformLatest pipeline (both react to the same authState
            // emission concurrently), so a `ready()` call could return a stale pre-sign-in `Local`
            // value read from the StateFlow's current value before migration had even started.
            session.filterIsInstance<DataSession.Remote>()
                .distinctUntilChangedBy { it.uid }
                .collect { remoteSession ->
                    val uid = remoteSession.uid
                    Log.d(TAG, "fcm/timezone sync: session is Remote for uid=$uid")
                    runCatching {
                        val zoneId = deviceTimeZoneId()
                        Log.d(TAG, "fcm/timezone sync: writing timeZone=$zoneId for uid=$uid")
                        remoteSession.notifications.setTimeZone(zoneId)
                        Log.d(TAG, "fcm/timezone sync: timeZone write succeeded")
                        val token = FirebaseMessaging.getInstance().token.await()
                        Log.d(TAG, "fcm/timezone sync: got FCM token, registering for uid=$uid")
                        fcmTokenRepository.registerToken(uid, token)
                        Log.d(TAG, "fcm/timezone sync: token registration succeeded")
                    }.onFailure { error ->
                        Log.e(TAG, "fcm/timezone sync: FAILED for uid=$uid", error)
                    }
                }
        }
        scope.launch {
            notificationDebugLog.entries.collect { entries ->
                notificationDebugEntries.clear()
                notificationDebugEntries.addAll(entries)
            }
        }
        scope.launch {
            authRepository.authState.collect { authState.value = it }
        }
        scope.launch {
            onboardingPreferences.observeHasCompletedOnboarding().collect { hasCompletedOnboarding.value = it }
        }
        scope.launch {
            groupPreferences.observeSelectedGroupIds().collect { persisted ->
                val resolved = resolveSelectedGroupIds(persisted, knownGroupIds, defaultThematicGroupIds)
                selectedGroupIds.value = resolved
                if (!draftInitialized) {
                    draftGroupIds.value = resolved
                    draftInitialized = true
                }
            }
        }
        scope.launch {
            // Same class of problem as the healer-grant collector above (design.md D8): reset the
            // initialized flag INSIDE flatMapLatest so a fresh (re)started flow -- cold start or a
            // sign-in/sign-out session swap -- only ever seeds entitlementTier, never fires a
            // spurious lapse notice for a session that happens to load an already-Free state.
            session.flatMapLatest { s ->
                entitlementFlowInitialized = false
                s.entitlements.observe()
            }.catch { error -> Log.e(TAG, "entitlement flow failed", error) }
                .collect { entitlement ->
                    // Compared against the repository-emitted value only, never a client-side
                    // optimistic purchase overlay (design.md D7/D8) -- an expiring optimistic
                    // window can never masquerade as a real lapse.
                    if (entitlementFlowInitialized &&
                        entitlementTier.value == AccessTier.PRO &&
                        entitlement.tier == AccessTier.FREE
                    ) {
                        proLapseNotice.value = true
                        selectedGroupIds.value?.let { committed ->
                            val updated = deselectLockedGroups(committed, proOnlyGroupIds, defaultThematicGroupIds)
                            selectedGroupIds.value = updated
                            draftGroupIds.value = updated
                            scope.launch { groupPreferences.saveSelectedGroupIds(updated) }
                        }
                    }
                    entitlementFlowInitialized = true
                    entitlementTier.value = entitlement.tier
                }
        }
    }

    fun completeOnboarding() {
        hasCompletedOnboarding.value = true
        val uid = (authState.value as? AuthState.SignedIn)?.uid
        scope.launch {
            onboardingPreferences.setCompleted()
            if (uid != null) {
                runCatching { onboardingRepository.markCompleted(uid) }
            }
        }
    }

    /** Used by the onboarding flow's "I already have an account" shortcut, right after sign-in,
     * to recognize a returning account and skip the question steps. `false` on any read failure
     * (e.g. offline) — falls back to the normal question flow rather than blocking onboarding. */
    suspend fun hasRemoteOnboardingCompleted(uid: String): Boolean =
        runCatching { onboardingRepository.hasCompleted(uid) }.getOrDefault(false)

    /** Starts Google sign-in from the Settings account section. Never crashes: recoverable
     * failures land in [authError]; cancellation clears it and leaves the user signed out. */
    fun signIn(activityContext: Context) {
        authError.value = null
        scope.launch {
            authRepository.signIn(AuthProviderId.GOOGLE, activityContext)
                .onFailure { throwable ->
                    authError.value = when (throwable) {
                        is SignInCancelledException -> null
                        is AuthException -> throwable.error
                        else -> AuthError.Unknown(throwable.message)
                    }
                }
        }
    }

    fun signOut() {
        scope.launch { authRepository.signOut() }
    }

    fun clearNotificationDebugLog() {
        scope.launch { notificationDebugLog.clear() }
    }

    /** Posts a real notification right now, bypassing the scheduler/worker chain entirely — lets
     * the user confirm what a delivered notification looks like without waiting for a scheduled slot. */
    fun sendTestNotification() {
        scope.launch {
            notifier.notify(
                channel = NotificationChannelSpec.REMINDER,
                title = "Notificación de prueba",
                body = "Si ves esto, el sistema de notificaciones funciona en este teléfono.",
            )
        }
    }

    /** Same as [sendTestNotification] but on the mood channel, so the mood-value action buttons
     * (and the tap-to-open-Ánimo behavior) can be tried without waiting for the nightly window. */
    fun sendTestMoodNotification() {
        scope.launch {
            notifier.notify(
                channel = NotificationChannelSpec.MOOD,
                title = "¿Cómo te sentiste hoy?",
                body = "Notificación de prueba: tocá un emoji para abrir tu ánimo de hoy con esa opción elegida.",
            )
        }
    }

    fun setChannelEnabled(channel: NotificationChannelSpec, enabled: Boolean) {
        scope.launch {
            ready().notifications.setEnabled(channel, enabled)
        }
    }

    /**
     * [NotificationChannelSpec.STREAK] has no configurable segments (spec: it fires once at a
     * fixed 20:00 user-local instant, evaluated server-side) — calling this for it is a no-op
     * rather than an exhaustive `when` branch, since there is nothing to persist.
     */
    fun setChannelSegments(channel: NotificationChannelSpec, segments: Set<DaySegment>) {
        if (channel == NotificationChannelSpec.STREAK) return
        scope.launch {
            ready().notifications.setSegments(channel, segments)
        }
    }

    fun setQuietHoursEnabled(enabled: Boolean) {
        scope.launch {
            ready().notifications.setQuietHoursEnabled(enabled)
        }
    }

    fun setQuietHoursWindow(startMinute: Int, endMinute: Int) {
        scope.launch {
            ready().notifications.setQuietHoursWindow(startMinute, endMinute)
        }
    }

    fun addAffirmationWithColor(title: String, subtitle: String, colorHex: String) {
        addImageError.value = null
        scope.launch {
            ready().affirmations.insert(
                Affirmation(
                    title = title,
                    subtitle = subtitle,
                    background = AffirmationBackground.Color(colorHex),
                ).toEntity()
            )
        }
    }

    fun addAffirmationWithImage(title: String, subtitle: String, imageUrl: String) {
        addImageError.value = null
        scope.launch {
            val localPath = runCatching { imageStore.download(imageUrl) }
                .onFailure { addImageError.value = "No se pudo descargar la imagen: ${it.message}" }
                .getOrNull() ?: return@launch
            insertImageAffirmation(title, subtitle, localPath)
        }
    }

    fun addAffirmationWithGalleryImage(title: String, subtitle: String, imageUri: Uri) {
        addImageError.value = null
        scope.launch {
            val localPath = runCatching { imageStore.importFromGallery(imageUri) }
                .onFailure { addImageError.value = "No se pudo importar la imagen: ${it.message}" }
                .getOrNull() ?: return@launch
            insertImageAffirmation(title, subtitle, localPath)
        }
    }

    private suspend fun insertImageAffirmation(title: String, subtitle: String, localPath: String) {
        ready().affirmations.insert(
            Affirmation(
                title = title,
                subtitle = subtitle,
                background = AffirmationBackground.Image(localPath),
            ).toEntity()
        )
    }

    fun importAffirmationsFromJson(json: String, replaceExisting: Boolean) {
        importAffirmationsError.value = null
        val parsed = try {
            parseAffirmationsJson(json)
        } catch (e: IllegalArgumentException) {
            importAffirmationsError.value = e.message
            return
        }

        scope.launch {
            val affirmationsRepo = ready().affirmations
            if (replaceExisting) {
                affirmationsRepo.deleteAll()
            }

            var failedCount = 0
            parsed.forEach { item ->
                val background = if (item.backgroundType == "image") {
                    val localPath = runCatching { imageStore.download(item.backgroundValue) }
                        .onFailure { failedCount++ }
                        .getOrNull() ?: return@forEach
                    AffirmationBackground.Image(localPath)
                } else {
                    AffirmationBackground.Color(item.backgroundValue)
                }

                affirmationsRepo.insert(
                    Affirmation(
                        title = item.title,
                        subtitle = item.subtitle,
                        background = background,
                    ).toEntity()
                )
            }

            if (failedCount > 0) {
                importAffirmationsError.value =
                    "$failedCount afirmación(es) no se pudieron importar: falló la descarga de la imagen."
            }
        }
    }

    fun removeAffirmation(id: String) {
        scope.launch { ready().affirmations.deleteById(id) }
    }

    /** Call once per affirmation the user settles on while swiping the feed. */
    fun recordAffirmationViewed() {
        scope.launch {
            val today = DayClock.epochDay()
            val viewed = affirmationsViewedToday
            val updated = if (viewed.epochDay == today) {
                viewed.copy(count = viewed.count + 1)
            } else {
                DailyViewCount(epochDay = today, count = 1)
            }
            affirmationsViewedToday = updated
            trackerPreferences.saveAffirmationsViewedToday(updated)
            if (updated.count >= AFFIRMATIONS_GOAL_PER_DAY) {
                ready().completions.markAffirmation(today)
                widgetUpdater.refresh()
            }
        }
    }

    /** Call when a meditation session finishes its full countdown. */
    fun recordMeditationCompleted() {
        scope.launch {
            ready().completions.markMeditation(DayClock.epochDay())
            widgetUpdater.refresh()
        }
    }

    /** Call from the day-detail sheet, for today or any past day the user is backfilling. */
    fun recordMood(epochDay: Long, moodValue: Int, note: String?) {
        scope.launch { ready().moods.upsert(epochDay, moodValue, note?.trim()?.ifBlank { null }) }
    }

    /**
     * Explicit user action for the streak-healer CTA. Deliberately re-reads [DayClock.epochDay]
     * and re-derives eligibility here rather than trusting the (possibly stale, e.g. a screen left
     * open across midnight) [streakHealer] state already rendered — a failed check is a silent
     * no-op (design.md's "Validate eligibility at click time, not from the rendered state" decision,
     * the same class of bug as the previously recorded UTC-vs-zone one).
     */
    fun activateStreakHealer() {
        scope.launch {
            val today = DayClock.epochDay()
            val start = healerStartEpochDay(today)
            val activeSession = ready()
            val rows = activeSession.completions.getRange(start, today)
            val uses = activeSession.healerUses.getRange(start, today)
            val activation = StreakHealerStats.evaluate(rows, uses, todayEpochDay = today, startEpochDay = start).activation
            if (activation is HealerActivation.Available) {
                activeSession.healerUses.recordUse(activation.breakEpochDay)
            }
        }
    }

    /** Call whenever the user settles on a new duration (slider release, preset tap). */
    fun recordMeditationDurationSelected(seconds: Int) {
        meditationDurationSeconds.value = seconds
        scope.launch { ready().meditation.saveMeditationDurationSeconds(seconds) }
    }

    /** Flips [groupId]'s membership in [draftGroupIds]. No-op for `alwaysSelected`/locked groups —
     * the UI also disables them; this is defense in depth. */
    fun toggleGroup(groupId: String, toggleable: Boolean) {
        if (!toggleable) return
        draftGroupIds.value = if (groupId in draftGroupIds.value) {
            draftGroupIds.value - groupId
        } else {
            draftGroupIds.value + groupId
        }
    }

    /** Commits [draftGroupIds] as [selectedGroupIds] and persists it, unless the draft violates
     * the minimum-selection invariant — in which case nothing is committed or persisted. Returns
     * whether the commit happened, so the caller can decide whether to collapse the sheet. */
    fun applyGroupSelection(): Boolean {
        if (!isDraftSelectionValid) return false
        val committed = draftGroupIds.value
        selectedGroupIds.value = committed
        scope.launch { groupPreferences.saveSelectedGroupIds(committed) }
        return true
    }

    /** Discards any uncommitted draft edits, restoring [draftGroupIds] to the last committed
     * [selectedGroupIds] — used when the sheet re-expands. */
    fun resetDraftToCommitted() {
        selectedGroupIds.value?.let { draftGroupIds.value = it }
    }

    /** Floors [StreakHealerStats] evaluation at [StreakHealerStats.EPOCH_START_DAY] so completion
     * history from before this feature shipped can never retroactively grant/heal (design.md's
     * "Migration / Rollout" decision), while still respecting the usual lookback window. */
    private fun healerStartEpochDay(todayEpochDay: Long): Long =
        StreakHealerStats.healerStartEpochDay(todayEpochDay)

    private companion object {
        const val TAG = "AffirmityAppState"
        const val AFFIRMATIONS_GOAL_PER_DAY = 5

        /** How far back to look when deriving the running streak from `daily_completion`. */
        const val STREAK_LOOKBACK_DAYS = StreakHealerStats.LOOKBACK_DAYS
    }
}

/**
 * Kill switch (design.md's "Migration/Rollout"): flip to `false` to force every session back to
 * [DataSession.Local] without reverting any code, e.g. if Firestore rules/rollout need a pause.
 */
private const val USE_REMOTE_SESSION = true

@Composable
fun rememberAffirmityAppState(): AffirmityAppState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Resolved here, in the composable body, not inside `remember` — a locale switch recreates
    // the Activity and re-runs this whole composable in a fresh Composition, so this always
    // reflects the current app locale (D6), unlike a value captured once inside `remember`.
    val dayLetters = stringArrayResource(R.array.weekday_letters).toList()
    // Resolved here (not inside `remember`) so the `AffirmityAppState` class itself never imports
    // `ui.groups` (design D9) — only this composable wiring function does, mirroring [dayLetters].
    val knownGroupIds = selectableAffirmationGroups().map { it.id }.toSet()
    val defaultThematicGroupIds = defaultAffirmationGroups()
        .filter { it.access == AffirmationGroupAccess.FREE }
        .map { it.id }.toSet()
    // Every group that requires Pro to unlock -- mirrors GroupAccessPolicy.isLocked's condition
    // (access != FREE && !alwaysSelected) without importing ui.groups' policy file itself (D9).
    val proOnlyGroupIds = defaultAffirmationGroups()
        .filter { it.access != AffirmationGroupAccess.FREE }
        .map { it.id }.toSet()
    return remember {
        val database = AffirmityDatabase.getInstance(context)
        val notificationPreferences = NotificationPreferences(context)
        val notificationDebugLog = NotificationDebugLog(context.applicationContext)
        val googleIdAuthProvider = GoogleIdAuthProvider(CredentialManager.create(context.applicationContext))
        val trackerPreferences = TrackerPreferences(context)
        val onboardingPreferences = OnboardingPreferences(context)
        val firestore = FirebaseFirestore.getInstance()
        val local = DataSession.Local(
            affirmations = RoomAffirmationRepository(database.affirmationDao()),
            completions = RoomDailyCompletionRepository(database.dailyCompletionDao()),
            moods = RoomDailyMoodRepository(database.dailyMoodDao()),
            healerUses = RoomStreakHealerRepository(database.streakHealerUseDao()),
            meditation = RoomMeditationPreferencesRepository(trackerPreferences),
            notifications = RoomNotificationSettingsRepository(notificationPreferences),
            adUnlocks = RoomAdUnlockRepository(database.adUnlockDao()),
        )
        AffirmityAppState(
            scope = scope,
            local = local,
            remoteSessionFactory = { uid ->
                DataSession.Remote(
                    uid = uid,
                    affirmations = FirestoreAffirmationRepository(firestore, uid),
                    completions = FirestoreDailyCompletionRepository(firestore, uid),
                    moods = FirestoreDailyMoodRepository(firestore, uid),
                    healerUses = FirestoreStreakHealerRepository(firestore, uid),
                    meditation = FirestoreMeditationPreferencesRepository(firestore, uid),
                    notifications = FirestoreNotificationSettingsRepository(firestore, uid),
                    entitlements = FirestoreEntitlementRepository(firestore, uid),
                    adUnlocks = FirestoreAdUnlockRepository(firestore, uid),
                )
            },
            migrator = FirestoreMigrator(firestore),
            trackerPreferences = trackerPreferences,
            onboardingPreferences = onboardingPreferences,
            imageStore = AffirmationImageStore(context.applicationContext),
            notificationDebugLog = notificationDebugLog,
            notifier = Notifier(context.applicationContext, notificationDebugLog),
            widgetUpdater = widgetUpdater(context.applicationContext),
            fcmTokenRepository = FcmTokenRepository(firestore),
            onboardingRepository = FirestoreOnboardingRepository(firestore),
            dayLetters = dayLetters,
            authRepository = FirebaseAuthRepository(
                auth = FirebaseAuth.getInstance(),
                providers = mapOf(AuthProviderId.GOOGLE to googleIdAuthProvider),
            ),
            useRemoteSession = USE_REMOTE_SESSION,
            groupPreferences = AffirmationGroupPreferences(context.applicationContext),
            knownGroupIds = knownGroupIds,
            defaultThematicGroupIds = defaultThematicGroupIds,
            proOnlyGroupIds = proOnlyGroupIds,
        )
    }
}

/** Pushes a Glance `updateAll` for [WeeklyTrackerWidget] (D9). */
private fun widgetUpdater(context: android.content.Context): WidgetUpdater = WidgetUpdater {
    WeeklyTrackerWidget().updateAll(context)
}
