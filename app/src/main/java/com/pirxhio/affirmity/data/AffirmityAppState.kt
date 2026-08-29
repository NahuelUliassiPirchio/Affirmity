package com.pirxhio.affirmity.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.credentials.CredentialManager
import com.pirxhio.affirmity.BuildConfig
import com.pirxhio.affirmity.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.pirxhio.affirmity.access.AccessDecision
import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.access.AdUnitIds
import com.pirxhio.affirmity.access.AdUnlockOutcome
import com.pirxhio.affirmity.access.AdUnlockPolicy
import com.pirxhio.affirmity.access.AdUnlockRecord
import com.pirxhio.affirmity.access.AdUnlockSource
import com.pirxhio.affirmity.access.AdUnlockState
import com.pirxhio.affirmity.access.ContentKey
import com.pirxhio.affirmity.access.ContentType
import com.pirxhio.affirmity.access.NoAdUnlockSource
import com.pirxhio.affirmity.access.RewardedAdUnlockSource
import com.google.firebase.analytics.FirebaseAnalytics
import com.pirxhio.affirmity.analytics.AnalyticsConsentState
import com.pirxhio.affirmity.analytics.AnalyticsEvent
import com.pirxhio.affirmity.analytics.AnalyticsId
import com.pirxhio.affirmity.analytics.AnalyticsLogger
import com.pirxhio.affirmity.analytics.ConsentGatedAnalyticsLogger
import com.pirxhio.affirmity.analytics.CreationMethod
import com.pirxhio.affirmity.analytics.DailyGoal
import com.pirxhio.affirmity.analytics.FirebaseAnalyticsLogger
import com.pirxhio.affirmity.analytics.NoOpAnalyticsLogger
import com.pirxhio.affirmity.analytics.firebase.AndroidFirebaseAnalyticsSink
import com.pirxhio.affirmity.analytics.toAdFailureReason
import com.pirxhio.affirmity.ads.GoogleRewardedAdGateway
import com.pirxhio.affirmity.ads.findActivity
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
import com.pirxhio.affirmity.data.local.OnboardingGuidePreferences
import com.pirxhio.affirmity.data.local.OnboardingPreferences
import com.pirxhio.affirmity.data.local.PERSONALIZADAS_GROUP_ID
import com.pirxhio.affirmity.data.local.QuietHoursSettings
import com.pirxhio.affirmity.data.local.TrackerPreferences
import com.pirxhio.affirmity.data.remote.FcmTokenRepository
import com.pirxhio.affirmity.data.remote.FirestoreAdUnlockRepository
import com.pirxhio.affirmity.data.remote.FirestoreAffirmationRepository
import com.pirxhio.affirmity.data.remote.FirestoreCatalogOverrideRepository
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
import com.pirxhio.affirmity.data.repository.CatalogAffirmationRepository
import com.pirxhio.affirmity.data.repository.DataSession
import com.pirxhio.affirmity.data.repository.FavoriteAffirmationRepository
import com.pirxhio.affirmity.data.repository.NoOpCatalogAffirmationRepository
import com.pirxhio.affirmity.data.repository.NoOpFavoriteAffirmationRepository
import com.pirxhio.affirmity.data.repository.RoomAdUnlockRepository
import com.pirxhio.affirmity.data.repository.RoomAffirmationRepository
import com.pirxhio.affirmity.data.repository.RoomCatalogAffirmationRepository
import com.pirxhio.affirmity.data.repository.RoomCatalogOverrideRepository
import com.pirxhio.affirmity.data.repository.RoomDailyCompletionRepository
import com.pirxhio.affirmity.data.repository.RoomDailyMoodRepository
import com.pirxhio.affirmity.data.repository.RoomFavoriteAffirmationRepository
import com.pirxhio.affirmity.data.repository.RoomMeditationPreferencesRepository
import com.pirxhio.affirmity.data.repository.RoomNotificationSettingsRepository
import com.pirxhio.affirmity.data.repository.RoomStreakHealerRepository
import com.pirxhio.affirmity.meditation.SessionEndReason
import com.pirxhio.affirmity.notifications.NotificationChannelSpec
import com.pirxhio.affirmity.notifications.Notifier
import com.pirxhio.affirmity.data.catalog.CATALOG_ID_PREFIX
import com.pirxhio.affirmity.ui.groups.catalogAccessDecision
import com.pirxhio.affirmity.ui.groups.catalogCollectionsById
import com.pirxhio.affirmity.ui.groups.catalogUniverseGroups
import com.pirxhio.affirmity.ui.groups.defaultAffirmationGroups
import com.pirxhio.affirmity.ui.groups.selectableAffirmationGroups
import com.pirxhio.affirmity.access.isUnlocked
import com.pirxhio.affirmity.ui.myaffirmations.customAffirmationAccessDecision
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val overrides: Map<String, String> = emptyMap(),
    /** Presentation-level provenance (design D14). The UI reads THIS to decide what to render
     *  (e.g. hide the delete affordance); write routing reads the id prefix instead, which is the
     *  storage-level ground truth and cannot drift from the row's real home. */
    val source: AffirmationSource = AffirmationSource.OWNED,
    /** The access unit for CATALOG rows (design D5/D6). Always null for OWNED rows. */
    val collectionId: String? = null,
)

/** See [Affirmation.source] (design D14). */
enum class AffirmationSource { OWNED, CATALOG }

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
    overrides = overrides,
)

/** Catalog row -> read-model [Affirmation] (design D8/D14): `text` maps to `title`, `subtitle` is
 *  empty (one authored string per affirmation, no split); the background is DERIVED, never stored
 *  (design D4); [overrides] comes from the per-user override map, keyed off the row's own id. */
private fun com.pirxhio.affirmity.data.local.CatalogAffirmationEntity.toAffirmation(
    overrides: Map<String, String>,
): Affirmation = Affirmation(
    id = id,
    title = text,
    subtitle = "",
    background = com.pirxhio.affirmity.ui.affirmations.forCatalogAffirmation(groupId, id),
    groupId = groupId,
    overrides = overrides,
    source = AffirmationSource.CATALOG,
    collectionId = collectionId,
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
    overrides = overrides,
)

/**
 * Pure resolution of the committed group-id selection, extracted so it is testable without
 * Android/DataStore (design §4, §6, D18). [persisted] is `null` on the very first-ever launch (no
 * selection ever saved). Unknown ids (e.g. a group removed in a later release, such as the 3
 * legacy groups deleted by design D17) are dropped. `personalizadas` is always force-included.
 *
 * The minimum-selection invariant lives HERE, tier-independent (design D18) -- moved out of
 * `EntitlementResolution.deselectLockedGroups`'s call site, which is guarded by
 * `if (entitlement.tier == AccessTier.FREE)` and therefore never ran for a Pro user. Without this,
 * a device holding a persisted selection that resolves to nothing thematic (either because every
 * persisted id was unknown, or because the persisted selection was itself empty) would land on a
 * `personalizadas`-only, thematically empty feed regardless of tier. Cannot touch a healthy
 * selection: any surviving non-personalizadas id short-circuits the fallback.
 */
fun resolveSelectedGroupIds(
    persisted: Set<String>?,
    knownIds: Set<String>,
    defaultThematicIds: Set<String>,
): Set<String> {
    val filtered = persisted?.filter { it in knownIds }?.toSet() ?: defaultThematicIds
    val resolved = if (filtered.none { it != PERSONALIZADAS_GROUP_ID }) defaultThematicIds else filtered
    return resolved + PERSONALIZADAS_GROUP_ID
}

/**
 * Pure migration-default resolution for the onboarding guide's tri-state "seen" flag (spec R1.3,
 * design D2), extracted so the legacy-install backfill is testable without DataStore.
 *
 * [guideSeen] is the raw DataStore read: `null` = key never written, `false` = armed (owed),
 * `true` = seen. [hasCompletedOnboarding] is the survey's own completion flag.
 *
 * - `guideSeen != null` (armed or already seen): passed through unchanged -- an explicit write
 *   always wins, regardless of [hasCompletedOnboarding].
 * - `guideSeen == null && hasCompletedOnboarding == true`: a pre-existing install from before this
 *   change shipped -- backfills to `true` (seen) so the auto-show gate can never retroactively
 *   fire for it (R1.3's locked decision).
 * - `guideSeen == null && hasCompletedOnboarding != true` (`false` or still-unresolved `null`):
 *   either onboarding is genuinely mid-flow or not yet resolved -- stays `null` (unresolved); the
 *   auto-show flag only ever gets armed explicitly by [AffirmityAppState.completeOnboarding]'s
 *   `arm()` call, never by this backfill.
 */
fun resolveGuideBackfill(guideSeen: Boolean?, hasCompletedOnboarding: Boolean?): Boolean? =
    when {
        guideSeen != null -> guideSeen
        hasCompletedOnboarding == true -> true
        else -> null
    }

/** Derives the auto-show boolean from the resolved tri-state (spec R1.2): shown only when the
 * resolved state is explicitly armed (`false`). `null` (unresolved) and `true` (seen) both mean
 * "don't show". */
fun shouldShowGuide(resolvedGuideSeen: Boolean?): Boolean = resolvedGuideSeen == false

/**
 * Gate-precedence helper (spec R6.2, design D3), extracted so [MainActivity]'s ordering can be
 * driven by a plain JUnit test. The auto-show guide gate MUST take precedence over
 * `healerJustGranted` whenever both are true on the same composition -- the guide renders first,
 * and the healer celebration stays un-consumed (its own state is untouched by this function) so it
 * still fires on the next composition after the guide is dismissed (covers the theoretical
 * collision in spec E4).
 */
enum class GuideGateResolution { AUTO_GUIDE, MANUAL_GUIDE, HEALER_GRANTED, NONE }

fun resolveGuideGate(
    autoShow: Boolean,
    manualShow: Boolean,
    healerJustGranted: Boolean,
): GuideGateResolution = when {
    autoShow -> GuideGateResolution.AUTO_GUIDE
    manualShow -> GuideGateResolution.MANUAL_GUIDE
    healerJustGranted -> GuideGateResolution.HEALER_GRANTED
    else -> GuideGateResolution.NONE
}

/** Default for tests/previews that don't care about group selection: never emits a persisted
 * value, so callers always resolve to the first-launch default. */
private object NoOpGroupSelectionPreferences : GroupSelectionPreferences {
    override fun observeSelectedGroupIds(): kotlinx.coroutines.flow.Flow<Set<String>?> =
        kotlinx.coroutines.flow.flowOf(null)
    override suspend fun saveSelectedGroupIds(ids: Set<String>) = Unit
}

/** One-shot ad-request outcome, mapped from [AdUnlockOutcome] for display (design D7). `Failed`
 *  and `Unavailable` collapse to the same [UNAVAILABLE] notice -- an SDK error string is not user
 *  copy -- while [DISMISSED] stays distinct, matching "you closed it early" vs "no ad available". */
enum class AdRequestNotice { EARNED, DISMISSED, UNAVAILABLE }

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
    private val onboardingGuidePreferences: OnboardingGuidePreferences,
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
    /** Every group id whose `access.requiredTier == AccessTier.PRO`, excluding `alwaysSelected`,
     * resolved by the caller for the same D9 reason. Consumed by the downgrade-auto-deselect
     * collector ([deselectLockedGroups] call site). */
    private val proOnlyGroupIds: Set<String> = emptySet(),
    /** The seam Spec 5 replaces (design §9): the only way an ad unlock is ever created.
     *  Defaulted to [NoAdUnlockSource] -- the same injection convention as [deviceTimeZoneId] /
     *  [groupPreferences] / [knownGroupIds] -- so Spec 5's entire integration into this class is
     *  one changed argument at the `rememberAffirmityAppState` call site. */
    private val adUnlockSource: AdUnlockSource = NoAdUnlockSource,
    private val favorites: FavoriteAffirmationRepository = NoOpFavoriteAffirmationRepository,
    /** Read-only shared catalog cache (design D9). Deliberately OUTSIDE [DataSession] -- it is
     *  byte-identical signed-in and signed-out, so it has no sign-in/sign-out swap semantics. */
    private val catalog: CatalogAffirmationRepository = NoOpCatalogAffirmationRepository,
    /** Spec 6's one frozen seam (design D1) -- defaulted to [NoOpAnalyticsLogger], the same
     *  injection convention as [adUnlockSource]. The composition root swaps this once for the
     *  real, consent-gated instance ([ConsentGatedAnalyticsLogger]) -- the one-line kill switch. */
    private val analytics: AnalyticsLogger = NoOpAnalyticsLogger,
) {
    val affirmations = mutableStateListOf<Affirmation>()

    /** Shared, read-only catalog rows currently in scope for the committed group selection
     *  (design D9/D10), with per-user overrides already applied. */
    private val catalogAffirmations = mutableStateListOf<Affirmation>()

    /** Both ID spaces, for favorites resolution (design D10). Concatenation, never a SQL union --
     *  when the session is Remote, [affirmations] is not in Room at all. */
    private val allAffirmations: List<Affirmation> get() = affirmations + catalogAffirmations

    var favoriteAffirmationIds = mutableStateOf<Set<String>>(emptySet())
        private set

    private var favoriteOrderedIds = mutableStateOf<List<String>>(emptyList())
    private val favoriteToggleMutex = Mutex()

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
        get() {
            val ids = selectedGroupIds.value ?: return affirmations
            val collectionsById = catalogCollectionsById()
            val groupsById = catalogUniverseGroups().associateBy { it.id }
            val now = System.currentTimeMillis()
            val tier = entitlementTier.value
            val grants = adUnlockState
            return affirmations.filter { it.groupId in ids } +
                catalogAffirmations.filter { affirmation ->
                    affirmation.groupId in ids &&
                        groupsById[affirmation.groupId]?.let { group ->
                            catalogAccessDecision(
                                group = group,
                                collection = collectionsById[affirmation.collectionId],
                                tier = tier,
                                grants = grants,
                                nowMillis = now,
                            ).isUnlocked
                        } == true
                }
        }

    /** Unchanged in shape; now resolves across BOTH id spaces (design D10). Access-unfiltered on
     *  purpose: a favorite made while Pro stays visible after a downgrade. */
    val favoriteAffirmations: List<Affirmation>
        get() {
            val byId = allAffirmations.associateBy { it.id }
            return favoriteOrderedIds.value.mapNotNull(byId::get)
        }

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

    /** The ONE ad request allowed in flight app-wide (REQ-4.8/6.1). Non-null = that key's CTA is
     *  busy and every ad CTA anywhere -- meditation catalog and affirmation groups alike -- is
     *  inert. UI-level half of the single-flight rule; [RewardedAdUnlockSource] holds the
     *  authoritative half so a non-UI caller cannot bypass it. */
    var adRequestInFlight = mutableStateOf<ContentKey?>(null)
        private set

    /** One-shot user-visible result of the last ad request (design D7). Mirrors [proLapseNotice] /
     *  [acknowledgeProLapse]'s snackbar consumption pattern exactly. */
    var adRequestNotice = mutableStateOf<AdRequestNotice?>(null)
        private set

    /** Dismisses [adRequestNotice] once the user has seen the snackbar (design D7). */
    fun acknowledgeAdRequestNotice() {
        adRequestNotice.value = null
    }

    private var entitlementFlowInitialized = false

    /** PER_USE ad unlocks earned in THIS process for THIS identity. Deliberately NOT in Room or
     *  Firestore: a PER_USE grant dies with the process by product definition (design §0/§4b).
     *  Cleared whenever the session identity changes (uid `null` <-> uid, see [session]) and on a
     *  live PRO->FREE transition (design §10 Q4(i)) -- at that instant it can only contain stale
     *  entries, since a PRO user can never acquire one by construction ([resolveAccess] returns
     *  `Unlocked` before ever reading grants). */
    var sessionAdUnlocks = mutableStateOf<Set<ContentKey>>(emptySet())
        private set

    /** Durable ONE_TIME_TRIAL grants, mirrored from the active session's `adUnlocks` repository
     *  (design §4b) -- same collector shape as [entitlementTier]. */
    var durableAdUnlocks = mutableStateOf<Map<ContentKey, AdUnlockRecord>>(emptyMap())
        private set

    /** TIMED_REPEATABLE grants, mirrored from the active session's `adUnlocks` repository's
     *  SEPARATE `observeTimedUnlocks()` stream (design D16) -- same collector shape as
     *  [durableAdUnlocks], deliberately not merged with it. */
    var timedAdUnlocks = mutableStateOf<Map<ContentKey, AdUnlockRecord>>(emptyMap())
        private set

    /** The complete grant state fed to `groupAccessDecision`/`resolveAccess` (design §9). */
    val adUnlockState: AdUnlockState
        get() = AdUnlockState(sessionAdUnlocks.value, durableAdUnlocks.value, timedAdUnlocks.value)

    /** Creation-time gate for the 4 custom-affirmation mutation surfaces (Spec 4, REQ-5.1/5.3).
     *  Derived on every read from [entitlementTier]/[adUnlockState] -- no polling, no recomposition
     *  logic beyond `collectAsState`. Count-independent: existing affirmations are never inspected
     *  (grandfathering, spec §0 Q3). */
    val customAffirmationCreateDecision: AccessDecision
        get() = customAffirmationAccessDecision(entitlementTier.value, adUnlockState, System.currentTimeMillis())

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

    /** True only when the post-survey onboarding guide is armed and not yet seen (spec R1.2, R1.3;
     * design D2/D3) -- drives [MainActivity]'s auto-show gate, positioned before every other
     * overlay gate. Resolved via [resolveGuideBackfill]/[shouldShowGuide] from the raw tri-state
     * DataStore read, never re-evaluated once true (see [markOnboardingGuideSeen]). */
    var shouldShowOnboardingGuide = mutableStateOf(false)
        private set

    /** Commits the guide as seen (spec R2.3, R4.2/R4.3) -- called by both the auto and manual
     * dismiss paths. Never re-arms the auto flag (R5.3/R5.4: the manual gate is a separate state
     * variable owned by [MainActivity]). */
    fun markOnboardingGuideSeen() {
        shouldShowOnboardingGuide.value = false
        scope.launch { onboardingGuidePreferences.markSeen() }
    }

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
            // A PER_USE grant belongs to a user, not a process (design §4b): every identity change
            // -- uid `null` <-> uid, including the very first emission at cold start -- clears it.
            sessionAdUnlocks.value = emptySet()
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
            favorites.observeFavoriteIds()
                .catch { error -> Log.e(TAG, "favorites flow failed", error) }
                .collect { ids ->
                    favoriteOrderedIds.value = ids
                    favoriteAffirmationIds.value = ids.toSet()
                }
        }
        scope.launch {
            session.flatMapLatest { it.affirmations.observeAll() }
                .catch { error -> Log.e(TAG, "affirmations flow failed", error) }
                .collect { entities ->
                    affirmations.clear()
                    affirmations.addAll(entities.map { it.toAffirmation() })
                }
        }
        scope.launch {
            // Two subscriptions with DELIBERATELY different lifetimes (design D9, revised): the
            // catalog rows survive an auth swap (byte-identical signed-in and signed-out); the
            // overrides half is session.flatMapLatest, matching every other per-user collector, so
            // signing out drops the previous user's overrides atomically.
            combine(
                snapshotFlow { selectedGroupIds.value.orEmpty() }
                    .flatMapLatest { catalog.observeByGroupIds(it) },
                session.flatMapLatest { it.catalogOverrides.observeAll() },
            ) { rows, overrides -> rows to overrides }
                .catch { error -> Log.e(TAG, "catalog flow failed", error) }
                .collect { (rows, overrides) ->
                    catalogAffirmations.clear()
                    catalogAffirmations.addAll(rows.map { it.toAffirmation(overrides[it.id].orEmpty()) })
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
            // D2 migration default: combines the raw tri-state guide-seen read with the survey's
            // already-held completion state (via `combine`, not a one-shot read of `.value`) so
            // ordering between the guide DataStore flow and completion-state updates can never
            // leave a legacy install
            // (guide-seen key absent, onboarding already complete) stuck unresolved -- see
            // resolveGuideBackfill's KDoc for the full truth table.
            combine(
                onboardingGuidePreferences.observeHasSeenGuide(),
                snapshotFlow { hasCompletedOnboarding.value },
            ) { rawGuideSeen, completedOnboarding -> rawGuideSeen to completedOnboarding }
                .collect { (rawGuideSeen, completedOnboarding) ->
                    val resolved = resolveGuideBackfill(rawGuideSeen, completedOnboarding)
                    shouldShowOnboardingGuide.value = shouldShowGuide(resolved)
                }
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
            // Durable ONE_TIME_TRIAL grants (design §4b) -- same flatMapLatest-per-swap shape as
            // every other session-scoped collector above, so a stale Firestore/Room subscription
            // can never survive a sign-in/sign-out swap.
            session.flatMapLatest { it.adUnlocks.observeDurableUnlocks() }
                .catch { error -> Log.e(TAG, "durable ad-unlock flow failed", error) }
                .collect { records -> durableAdUnlocks.value = records.associateBy(AdUnlockRecord::key) }
        }
        scope.launch {
            // TIMED_REPEATABLE grants (design D16) -- same flatMapLatest-per-swap shape as the
            // durable collector above, but reading the SEPARATE `observeTimedUnlocks()` stream.
            session.flatMapLatest { it.adUnlocks.observeTimedUnlocks() }
                .catch { error -> Log.e(TAG, "timed ad-unlock flow failed", error) }
                .collect { records -> timedAdUnlocks.value = records.associateBy(AdUnlockRecord::key) }
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
                    // window can never masquerade as a real lapse. proLapseNotice stays scoped to
                    // a LIVE Pro->Free transition only (design §10 Q4(iv)) -- it is a one-time
                    // snackbar, not a general "you are Free" indicator.
                    if (entitlementFlowInitialized &&
                        entitlementTier.value == AccessTier.PRO &&
                        entitlement.tier == AccessTier.FREE
                    ) {
                        proLapseNotice.value = true
                        // design §10 Q4(i): enforced, not just argued -- a PRO user can never
                        // acquire a PER_USE grant by construction, so at the instant of a live
                        // downgrade this set can only hold stale entries.
                        sessionAdUnlocks.value = emptySet()
                    }
                    // Q4(iv) fix: run the deselect sweep on EVERY FREE-resolved emission, not only
                    // a live transition -- otherwise a stale Pro-only group left in a persisted
                    // selection (a lapse that happened while the app was closed, or a dead PER_USE
                    // grant) would silently survive forever. Re-persist only when the result
                    // actually differs, so a steady-state Free user causes no redundant DataStore
                    // write on every emission. Living inside this collector (not the
                    // group-preferences collector) guarantees it never fires before the tier has
                    // resolved, so a cold start can never strip a Pro user's groups.
                    if (entitlement.tier == AccessTier.FREE) {
                        selectedGroupIds.value?.let { committed ->
                            val updated = deselectLockedGroups(committed, proOnlyGroupIds, defaultThematicGroupIds)
                            if (updated != committed) {
                                selectedGroupIds.value = updated
                                draftGroupIds.value = updated
                                scope.launch { groupPreferences.saveSelectedGroupIds(updated) }
                            }
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
        // R1.1/R7.1: arms the guide auto-show flag as one atomic addition to survey completion --
        // the ONLY code path allowed to arm it (spec R1.1). Fire-and-forget like the other writes
        // in this method; the collector above picks up the write once it lands.
        scope.launch { onboardingGuidePreferences.arm() }
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
        if (!customAffirmationCreateDecision.isUnlocked) return
        addImageError.value = null
        scope.launch {
            ready().affirmations.insert(
                Affirmation(
                    title = title,
                    subtitle = subtitle,
                    background = AffirmationBackground.Color(colorHex),
                ).toEntity()
            )
            // REQ-5.5/17: emitted after insert() succeeds -- creation_method is the entire
            // payload, never affirmation text (REQ-4.8).
            analytics.log(AnalyticsEvent.CustomAffirmationCreated(CreationMethod.COLOR))
        }
    }

    fun addAffirmationWithImage(title: String, subtitle: String, imageUrl: String) {
        if (!customAffirmationCreateDecision.isUnlocked) return
        addImageError.value = null
        scope.launch {
            val localPath = runCatching { imageStore.download(imageUrl) }
                .onFailure { addImageError.value = "No se pudo descargar la imagen: ${it.message}" }
                .getOrNull() ?: return@launch
            // D10.4: emit only after the download guard above -- a failed download must never
            // report a creation that never happened.
            insertImageAffirmation(title, subtitle, localPath, CreationMethod.IMAGE_URL)
        }
    }

    fun addAffirmationWithGalleryImage(title: String, subtitle: String, imageUri: Uri) {
        if (!customAffirmationCreateDecision.isUnlocked) return
        addImageError.value = null
        scope.launch {
            val localPath = runCatching { imageStore.importFromGallery(imageUri) }
                .onFailure { addImageError.value = "No se pudo importar la imagen: ${it.message}" }
                .getOrNull() ?: return@launch
            // D10.4: emit only after the import guard above -- a failed import must never report
            // a creation that never happened.
            insertImageAffirmation(title, subtitle, localPath, CreationMethod.GALLERY)
        }
    }

    private suspend fun insertImageAffirmation(title: String, subtitle: String, localPath: String, method: CreationMethod) {
        ready().affirmations.insert(
            Affirmation(
                title = title,
                subtitle = subtitle,
                background = AffirmationBackground.Image(localPath),
            ).toEntity()
        )
        analytics.log(AnalyticsEvent.CustomAffirmationCreated(method))
    }

    fun importAffirmationsFromJson(json: String, replaceExisting: Boolean) {
        // CRITICAL (D4): this guard MUST be the first statement -- strictly before deleteAll() is
        // ever reached below. A guard placed inside the forEach/after the replaceExisting wipe
        // would delete a Free user's entire table and then import nothing: data loss caused by the
        // gate itself. Blocked = zero side effects of any kind (no parse, no deleteAll, no insert).
        if (!customAffirmationCreateDecision.isUnlocked) {
            importAffirmationsError.value = "Importar afirmaciones requiere una suscripción Pro."
            return
        }
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
                favorites.clear()
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
        // Load-bearing guard (design D14): without it, a catalog id would reach
        // `ready().affirmations.deleteById`, a silent no-op on Room but a REAL per-user Firestore
        // write when the session is Remote -- a tombstone in a collection that must never contain
        // catalog ids. Catalog rows have no delete affordance in the UI; this is a hard backstop.
        if (id.startsWith(CATALOG_ID_PREFIX)) return
        scope.launch {
            ready().affirmations.deleteById(id)
            favorites.remove(id)
            analytics.log(AnalyticsEvent.CustomAffirmationDeleted)
        }
    }

    fun toggleFavorite(id: String) {
        scope.launch {
            favoriteToggleMutex.withLock {
                if (favorites.isFavorite(id)) {
                    favorites.remove(id)
                } else {
                    favorites.add(id, System.currentTimeMillis())
                }
            }
        }
    }

    /** Remove-only action for the Favorites screen. Repeated or stale callbacks stay idempotent. */
    fun removeFavorite(id: String) {
        scope.launch { favorites.remove(id) }
    }

    /**
     * Sets or clears a per-user token override on [affirmationId]. No entitlement guard by
     * design (design.md D13): placeholder editing is free for all users. Never emits an
     * analytics event (design.md D14): override values are free-text and PII-representable.
     */
    fun setTokenOverride(affirmationId: String, tokenKey: String, rawValue: String) {
        scope.launch {
            // Prefix routing (design D14): the id decides WHICH store, not a presentation flag --
            // a flag could drift from the row's actual home and send a catalog write into
            // `users/{uid}/affirmations`.
            val current = allAffirmations.firstOrNull { it.id == affirmationId } ?: return@launch
            val next = current.overrides.toMutableMap().apply {
                when (val normalized = AffirmationTemplateParser.normalizeOverrideValue(rawValue)) {
                    null -> remove(tokenKey) // empty input == revert to the authored original
                    else -> put(tokenKey, normalized)
                }
            }
            val pruned = AffirmationTemplateParser.pruneOverrides(current.title, current.subtitle, next)
            if (affirmationId.startsWith(CATALOG_ID_PREFIX)) {
                ready().catalogOverrides.setOverrides(affirmationId, pruned)
            } else {
                ready().affirmations.setOverrides(affirmationId, pruned)
            }
        }
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
                // D9: emit on the exact crossing (==), not on every subsequent >= call this day --
                // recordAffirmationViewed is idempotent-repeat-called, the >= branch above is
                // unchanged, this is a nested, additional condition.
                if (updated.count == AFFIRMATIONS_GOAL_PER_DAY) {
                    analytics.log(AnalyticsEvent.DailyGoalReached(DailyGoal.AFFIRMATION))
                }
            }
        }
    }

    /** In-memory per-process day guard (D9) -- recordMeditationCompleted has no counter to test a
     *  crossing against, so this bounds `daily_goal_reached(MEDITATION)` to at most once per
     *  process launch per day. Accepted limitation: a process restart plus a second completion the
     *  same day can double-count -- bounded, denominator-only, cheaper than a schema change. */
    private var meditationGoalEmittedEpochDay: Long? = null

    /**
     * Call when a meditation session finishes its full countdown. [startMillis]/[endMillis] are
     * wall-clock timestamps (`System.currentTimeMillis()`, not the monotonic clock the session
     * timer itself runs on) so a session crossing local midnight is archived on the day it mostly
     * ran on, via [DayClock.attributedEpochDay] -- not always the day it happened to finish on.
     */
    fun recordMeditationCompleted(startMillis: Long, endMillis: Long = System.currentTimeMillis()) {
        scope.launch {
            val day = DayClock.attributedEpochDay(startMillis, endMillis)
            ready().completions.markMeditation(day)
            widgetUpdater.refresh()
            if (day != meditationGoalEmittedEpochDay) {
                meditationGoalEmittedEpochDay = day
                analytics.log(AnalyticsEvent.DailyGoalReached(DailyGoal.MEDITATION))
            }
        }
    }

    /** UI-originated emit surface (design D1) for interaction events this class does not already
     *  own emitting itself (taps, screen renders) -- routes through the same injected [analytics]
     *  instance so the composition-root kill switch covers every event, not only this class's own
     *  emit points. */
    fun logAnalyticsEvent(event: AnalyticsEvent) {
        analytics.log(event)
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

    /**
     * The single outcome -> persistence orchestration for the ad-unlock seam (design §9). Calls
     * [adUnlockSource]; on [AdUnlockOutcome.Earned] only, routes [AdUnlockPolicy.PER_USE] into the
     * in-memory [sessionAdUnlocks], [AdUnlockPolicy.ONE_TIME_TRIAL] into the active session's
     * durable `adUnlocks` repository, and [AdUnlockPolicy.TIMED_REPEATABLE] into that same
     * repository's SEPARATE `timedUnlocks` store (design D16) with an expiry computed from
     * [unlockWindowHours]. Any other outcome (Dismissed/Failed/Unavailable) is a no-op.
     *
     * [unlockWindowHours] is the content's declared window (`collection.access.unlockWindowHours`
     * for a catalog collection); trailing-default so every existing call site keeps compiling
     * unchanged.
     */
    fun requestAdUnlock(key: ContentKey, policy: AdUnlockPolicy, unlockWindowHours: Int? = null) {
        if (adRequestInFlight.value != null) {
            analytics.log(AnalyticsEvent.AdUnlockTapIgnored(AnalyticsId.of(key)))
            return // taps are IGNORED, never queued (REQ-4.8)
        }
        adRequestInFlight.value = key
        analytics.log(AnalyticsEvent.AdUnlockRequested(AnalyticsId.of(key), policy))
        scope.launch {
            try {
                when (val outcome = adUnlockSource.requestUnlock(key, policy)) {
                    AdUnlockOutcome.Earned -> {
                        when (policy) {
                            AdUnlockPolicy.PER_USE -> sessionAdUnlocks.value = sessionAdUnlocks.value + key
                            AdUnlockPolicy.ONE_TIME_TRIAL -> ready().adUnlocks.grantDurableUnlock(
                                AdUnlockRecord(key, System.currentTimeMillis(), expiresAtMillis = null),
                            )
                            AdUnlockPolicy.TIMED_REPEATABLE -> {
                                val hours = unlockWindowHours ?: return@launch // never grants an unbounded window
                                val now = System.currentTimeMillis()
                                ready().adUnlocks.grantTimedUnlock(
                                    AdUnlockRecord(key, now, expiresAtMillis = now + hours * 3_600_000L),
                                )
                            }
                            AdUnlockPolicy.NONE -> Unit
                        }
                        analytics.log(AnalyticsEvent.AdUnlockEarned(AnalyticsId.of(key), policy))
                        adRequestNotice.value = AdRequestNotice.EARNED
                    }
                    AdUnlockOutcome.Dismissed -> {
                        analytics.log(AnalyticsEvent.AdUnlockDismissed(AnalyticsId.of(key), policy))
                        adRequestNotice.value = AdRequestNotice.DISMISSED
                    }
                    is AdUnlockOutcome.Failed -> {
                        Log.w(TAG, "ad unlock failed for $key: ${outcome.reason}")
                        analytics.log(
                            AnalyticsEvent.AdUnlockFailed(AnalyticsId.of(key), policy, outcome.reason.toAdFailureReason()),
                        )
                        adRequestNotice.value = AdRequestNotice.UNAVAILABLE
                    }
                    AdUnlockOutcome.Unavailable -> {
                        analytics.log(AnalyticsEvent.AdUnlockUnavailable(AnalyticsId.of(key), policy))
                        adRequestNotice.value = AdRequestNotice.UNAVAILABLE
                    }
                }
            } finally {
                adRequestInFlight.value = null // `finally`: cancellation must not wedge the CTA
            }
        }
    }

    /**
     * Consumes a meditation playback-scoped unlock when its session reaches a terminal state
     * (design §5.5, REQ-5.5). Synchronous, no coroutine -- mutates in-memory [sessionAdUnlocks]
     * only, matching [toggleGroup]/`applyGroupSelection`'s pattern. Nothing is persisted here:
     * `PER_USE` is never persisted (design §4.2), and a durable `ONE_TIME_TRIAL` grant is untouched
     * by [consumePlaybackScopedUnlock] (it only ever touches [sessionAdUnlocks]) -- see EC-3.
     */
    fun consumeMeditationPlaybackUnlock(entryId: String, reason: SessionEndReason) {
        sessionAdUnlocks.value = consumePlaybackScopedUnlock(
            sessionAdUnlocks.value, ContentKey(ContentType.MEDITATION, entryId), reason,
        )
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
        // design §0/§4b: a PER_USE unlock on an affirmation group is spent the moment its group
        // leaves the committed selection.
        sessionAdUnlocks.value = retainSelectionScopedUnlocks(sessionAdUnlocks.value, committed)
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
    // `isThematic` is the SELECTOR -- "every thematic group is on by default" is the product
    // decision (design D18). The tier condition is retained purely as a GUARD: it keeps the
    // invariant that the fresh-install default can never contain a group
    // `deselectLockedGroups` would immediately strip. Post-D17 (14 Free-tier universes, no more
    // legacy groups) this evaluates to all 14.
    val defaultThematicGroupIds = defaultAffirmationGroups()
        .filter { it.isThematic && it.access.requiredTier == AccessTier.FREE }
        .map { it.id }.toSet()
    // Every group that requires Pro to unlock -- mirrors GroupAccessPolicy.isLocked's condition
    // (access.requiredTier == PRO && !alwaysSelected) without importing ui.groups' policy file
    // itself (D9). Post-D17 this evaluates EMPTY: all 14 universe groups are declared
    // ContentAccess.Free at the group level (collection-level Pro gating moved to
    // CatalogAccessPolicy.catalogAccessDecision, design D6/D7). deselectLockedGroups (below) is a
    // documented no-op in that state, NOT deleted -- see task 4.7 / EntitlementResolutionTest's
    // regression guard.
    val proOnlyGroupIds = defaultAffirmationGroups()
        .filter { it.access.requiredTier != AccessTier.FREE }
        .map { it.id }.toSet()
    return remember {
        val database = AffirmityDatabase.getInstance(context)
        val notificationPreferences = NotificationPreferences(context)
        val notificationDebugLog = NotificationDebugLog(context.applicationContext)
        val googleIdAuthProvider = GoogleIdAuthProvider(CredentialManager.create(context.applicationContext))
        val trackerPreferences = TrackerPreferences(context)
        val onboardingPreferences = OnboardingPreferences(context)
        val onboardingGuidePreferences = OnboardingGuidePreferences(context)
        val firestore = FirebaseFirestore.getInstance()
        val local = DataSession.Local(
            affirmations = RoomAffirmationRepository(database.affirmationDao()),
            completions = RoomDailyCompletionRepository(database.dailyCompletionDao()),
            moods = RoomDailyMoodRepository(database.dailyMoodDao()),
            healerUses = RoomStreakHealerRepository(database.streakHealerUseDao()),
            meditation = RoomMeditationPreferencesRepository(trackerPreferences),
            notifications = RoomNotificationSettingsRepository(notificationPreferences),
            adUnlocks = RoomAdUnlockRepository(database.adUnlockDao(), database.timedAdUnlockDao()),
            catalogOverrides = RoomCatalogOverrideRepository(database.catalogOverrideDao()),
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
                    catalogOverrides = FirestoreCatalogOverrideRepository(firestore, uid),
                )
            },
            migrator = FirestoreMigrator(firestore),
            trackerPreferences = trackerPreferences,
            onboardingPreferences = onboardingPreferences,
            onboardingGuidePreferences = onboardingGuidePreferences,
            imageStore = AffirmationImageStore(context.applicationContext),
            notificationDebugLog = notificationDebugLog,
            notifier = Notifier(context.applicationContext, notificationDebugLog),
            widgetUpdater = widgetUpdater(context.applicationContext),
            fcmTokenRepository = FcmTokenRepository(firestore),
            onboardingRepository = FirestoreOnboardingRepository(firestore),
            favorites = RoomFavoriteAffirmationRepository(database.favoriteAffirmationDao()),
            catalog = RoomCatalogAffirmationRepository(database.catalogAffirmationDao()),
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
            adUnlockSource = RewardedAdUnlockSource(
                gateway = GoogleRewardedAdGateway(
                    // Re-resolved per call from the composable's own captured `context`, never
                    // retained past this composition (design D1) -- a locale switch/Activity
                    // recreation re-runs this whole composable, so `remember` closes over the
                    // NEW context on the next recomposition.
                    activityProvider = { context.findActivity() },
                    testDeviceHash = BuildConfig.ADMOB_TEST_DEVICE_HASH,
                    isDebug = BuildConfig.DEBUG,
                ),
                adUnitIds = AdUnitIds(
                    perUse = BuildConfig.ADMOB_REWARDED_UNIT_PER_USE,
                    oneTimeTrial = BuildConfig.ADMOB_REWARDED_UNIT_ONE_TIME_TRIAL,
                    timedRepeatable = BuildConfig.ADMOB_REWARDED_UNIT_TIMED_REPEATABLE,
                ),
            ),
            analytics = ConsentGatedAnalyticsLogger(
                // Real delegate as of PR7 -- delivers nothing end-to-end until spec §9.1 item 4
                // (Firebase console Analytics enablement + a regenerated google-services.json) is
                // done; until then this is exactly as inert as NoOpAnalyticsLogger was.
                delegate = FirebaseAnalyticsLogger(
                    AndroidFirebaseAnalyticsSink(FirebaseAnalytics.getInstance(context.applicationContext)),
                ),
                // PD-1: default-DENY, globally. UNKNOWN and DENIED both suppress fully. This
                // lambda is the ONE place a future consent surface (spec §9.1 item 3) plugs in
                // (design D5) -- swapping this whole `analytics` argument for NoOpAnalyticsLogger
                // is the one-line kill switch (design D1/REQ-4.6).
                consentState = { AnalyticsConsentState.UNKNOWN },
            ),
        )
    }
}

/** Pushes a Glance `updateAll` for [WeeklyTrackerWidget] (D9). */
private fun widgetUpdater(context: android.content.Context): WidgetUpdater = WidgetUpdater {
    WeeklyTrackerWidget().updateAll(context)
}
