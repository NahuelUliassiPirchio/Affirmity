package com.pirxhio.affirmity

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.pirxhio.affirmity.auth.AuthState
import com.pirxhio.affirmity.billing.BillingService
import com.pirxhio.affirmity.access.AccessDecision
import com.pirxhio.affirmity.analytics.AnalyticsEvent
import com.pirxhio.affirmity.analytics.AnalyticsId
import com.pirxhio.affirmity.analytics.PaywallPlan
import com.pirxhio.affirmity.analytics.PaywallSource
import com.pirxhio.affirmity.analytics.provenance
import com.pirxhio.affirmity.data.AdRequestNotice
import com.pirxhio.affirmity.data.Affirmation
import com.pirxhio.affirmity.data.GuideGateResolution
import com.pirxhio.affirmity.data.MOOD_MAX
import com.pirxhio.affirmity.data.rememberAffirmityAppState
import com.pirxhio.affirmity.data.resolveGuideGate
import com.pirxhio.affirmity.meditation.SessionEndReason
import com.pirxhio.affirmity.notifications.NotificationChannelSpec
import com.pirxhio.affirmity.ui.affirmations.AffirmationsScreen
import com.pirxhio.affirmity.ui.components.FloatingStatusOverlay
import com.pirxhio.affirmity.ui.favorites.FavoritesScreen
import com.pirxhio.affirmity.ui.feed.SeeAllThemesScreen
import com.pirxhio.affirmity.ui.feed.SurfaceDetailBottomSheet
import com.pirxhio.affirmity.ui.feed.YourFeedSheetContent
import com.pirxhio.affirmity.ui.feed.defaultRecommendedSurfaces
import com.pirxhio.affirmity.ui.groups.catalogThemesById
import com.pirxhio.affirmity.ui.groups.isThemeToggleable
import com.pirxhio.affirmity.ui.groups.themeAccessDecision
import com.pirxhio.affirmity.ui.healer.StreakHealerGrantedScreen
import com.pirxhio.affirmity.ui.meditation.GuidedMeditationScreen
import com.pirxhio.affirmity.ui.meditation.MeditationScreen
import com.pirxhio.affirmity.ui.meditation.catalog.findMeditationCatalogEntry
import com.pirxhio.affirmity.ui.meditation.catalog.isMeditationLocked
import com.pirxhio.affirmity.ui.meditation.catalog.meditationAccessDecision
import com.pirxhio.affirmity.ui.meditation.catalog.meditationCatalog
import com.pirxhio.affirmity.ui.meditation.catalog.meditationContentKey
import com.pirxhio.affirmity.ui.mood.MoodScreen
import com.pirxhio.affirmity.ui.myaffirmations.MyAffirmationsScreen
import com.pirxhio.affirmity.ui.onboarding.OnboardingScreen
import com.pirxhio.affirmity.ui.onboarding.guide.OnboardingGuideScreen
import com.pirxhio.affirmity.ui.paywall.PaywallSheet
import com.pirxhio.affirmity.ui.progress.ProgressScreen
import com.pirxhio.affirmity.ui.settings.NotificationDebugScreen
import com.pirxhio.affirmity.ui.settings.SettingsScreen
import com.pirxhio.affirmity.ui.theme.AffirmityTheme
import com.pirxhio.affirmity.data.local.AffirmityDatabase
import com.pirxhio.affirmity.data.repository.RoomCatalogAffirmationRepository
import com.pirxhio.affirmity.data.repository.RoomMeditationCustomizationRepository
import com.pirxhio.affirmity.ui.meditation.customization.affirmationTextsForBreathingAffirmations
import com.pirxhio.affirmity.meditation.customization.resolvedValues
import com.pirxhio.affirmity.ui.meditation.customization.MeditationCustomizationScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Extra key a launcher (e.g. the home-screen widget) sets to pick the initial [AppDestinations]. */
const val EXTRA_START_DESTINATION = "start_destination"

/** Extra key the reflection notification's mood-value actions set to pre-select a value in
 * [com.pirxhio.affirmity.ui.mood.MoodScreen]'s today sheet. */
const val EXTRA_MOOD_VALUE = "mood_value"

/** Unknown or absent values fall back to [AppDestinations.AFIRMACIONES] (D10). */
private fun resolveStartDestination(intent: Intent?): AppDestinations {
    val raw = intent?.getStringExtra(EXTRA_START_DESTINATION)
    return AppDestinations.entries.find { it.name == raw } ?: AppDestinations.AFIRMACIONES
}

private fun resolveMoodValue(intent: Intent?): Int? {
    val value = intent?.getIntExtra(EXTRA_MOOD_VALUE, -1) ?: -1
    return value.takeIf { it in 1..MOOD_MAX }
}

/**
 * Route-lookup fall-through for a persisted/restored `selectedMeditationEntryId` (REQ-5.4.3,
 * AC15, EC-4). Extracted out of [AffirmityApp] so the "unknown id resolves to no entry, and the
 * caller simply never composes [com.pirxhio.affirmity.ui.meditation.GuidedMeditationScreen]"
 * behavior can be driven directly by a plain JUnit test rather than only static inspection.
 * [findMeditationCatalogEntry] itself already returns null for an unknown id (covered by
 * `MeditationCatalogTest`); this function is the exact expression the composable evaluates on
 * every recomposition to decide whether the guided-session route renders at all.
 */
internal fun resolveSelectedMeditationEntry(
    selectedMeditationEntryId: String?,
): com.pirxhio.affirmity.ui.meditation.catalog.MeditationCatalogEntry? =
    selectedMeditationEntryId?.let { findMeditationCatalogEntry(it) }

/**
 * Launch-time access re-check predicate (REQ-5.4.1, AC14, EC-5). Extracted out of [AffirmityApp]
 * so a plain JUnit test can drive the exact same re-resolution the composable performs at
 * composition time -- proving that a grant/tier that changed between the Discover tap and this
 * render is picked up (never reused from a stale tap-time decision) without needing a Compose
 * test harness.
 */
internal fun isMeditationLaunchBlocked(
    entry: com.pirxhio.affirmity.ui.meditation.catalog.MeditationCatalogEntry,
    tier: com.pirxhio.affirmity.access.AccessTier,
    grants: com.pirxhio.affirmity.access.AdUnlockState,
    nowMillis: Long,
): Boolean = isMeditationLocked(meditationAccessDecision(entry, tier, grants, nowMillis))

/** The one catalog entry whose session cannot start straight from [MeditationLaunchStep.StartSession]'s
 * `customization` map: its affirmation phase needs real content fetched from the (separate)
 * affirmations feature first (see [affirmationTextsForBreathingAffirmations]), which is async and
 * therefore cannot happen inside [MeditationCatalogEntry.definition] itself. Every other entry is
 * provably unaffected by that enrichment step -- it's gated on this exact id, nowhere else. */
private const val BREATHING_AFFIRMATIONS_ENTRY_ID = "breathing_affirmations"

/**
 * The two possible outcomes of [decideMeditationLaunchStep]: an already-unlocked entry either
 * needs its pre-session customization screen shown first, or is ready to go straight into
 * [com.pirxhio.affirmity.ui.meditation.GuidedMeditationScreen] with a resolved config map.
 */
internal sealed interface MeditationLaunchStep {
    /** [seedValues] is what [com.pirxhio.affirmity.ui.meditation.customization.MeditationCustomizationScreen]
     * should be initialized with -- the saved per-device values with any field the current
     * `customizationFields` spec still declares but [savedValues] is missing filled in from that
     * field's own default (see [resolvedValues]). */
    data class ShowCustomization(val seedValues: Map<String, String>) : MeditationLaunchStep

    /** [customization] is the exact map [com.pirxhio.affirmity.ui.meditation.GuidedMeditationScreen]
     * passes through to `entry.definition(...)`: `emptyMap()` for a no-fields entry (skip-through,
     * matching pre-customization behavior), or the confirmed values once the user has confirmed
     * the customization screen. */
    data class StartSession(val customization: Map<String, String>) : MeditationLaunchStep
}

/**
 * Pre-session customization decision (spec: meditation-customization, REQ-5.4). Extracted out of
 * [AffirmityApp] so a plain JUnit test can drive the exact "show customization vs skip straight to
 * the session, and what values/config to hand each destination" branch the composable evaluates on
 * every recomposition of an unlocked meditation entry -- without needing a Compose test
 * harness (this repo has none; see `MeditationCustomizationDecisionTest`).
 *
 * Only called once [entry] has already passed the [isMeditationLaunchBlocked] access re-check, so
 * "is this entry unlocked" is never re-decided here.
 */
internal fun decideMeditationLaunchStep(
    entry: com.pirxhio.affirmity.ui.meditation.catalog.MeditationCatalogEntry,
    confirmedCustomization: Map<String, String>?,
    savedValues: Map<String, String>,
): MeditationLaunchStep =
    if (entry.customizationFields.isNotEmpty() && confirmedCustomization == null) {
        MeditationLaunchStep.ShowCustomization(
            seedValues = resolvedValues(entry.customizationFields, savedValues),
        )
    } else {
        MeditationLaunchStep.StartSession(customization = confirmedCustomization ?: emptyMap())
    }

/**
 * Guided-completion streak bookkeeping (REQ-5.6, AC6). Extracted out of [AffirmityApp]'s
 * `onSessionEnded` callback so a plain JUnit test can prove [recordMeditationCompleted] is
 * invoked if and only if [reason] is [SessionEndReason.Completed], while [consumePlaybackUnlock]
 * runs for every terminal reason -- without needing a Compose test harness.
 *
 * Spec 6 (D7, REQ-5.2): [elapsedSeconds] and [emit] are appended, existing call order preserved
 * EXACTLY -- [consumePlaybackUnlock] first, then [recordMeditationCompleted] only for `Completed`,
 * [emit] always last so a thrown analytics call can never reorder or skip either existing effect.
 */
internal fun handleGuidedMeditationSessionEnded(
    entryId: String,
    reason: SessionEndReason,
    elapsedSeconds: Long,
    accessDecision: AccessDecision,
    consumePlaybackUnlock: (String, SessionEndReason) -> Unit,
    recordMeditationCompleted: () -> Unit,
    emit: (AnalyticsEvent) -> Unit = {},
) {
    consumePlaybackUnlock(entryId, reason)
    if (reason == SessionEndReason.Completed) recordMeditationCompleted()
    val entry = findMeditationCatalogEntry(entryId) ?: return
    val analyticsId = AnalyticsId.of(entry)
    emit(
        if (reason == SessionEndReason.Completed) {
            AnalyticsEvent.MeditationCompleted(analyticsId, accessDecision.provenance(), elapsedSeconds)
        } else {
            AnalyticsEvent.MeditationCancelled(analyticsId, elapsedSeconds)
        },
    )
}

class MainActivity : AppCompatActivity() {
    private val startDestination = mutableStateOf(AppDestinations.AFIRMACIONES)
    private val startMoodValue = mutableStateOf<Int?>(null)

    /** Keeps the native cold-start splash (Theme.Affirmity.Starting) on screen until onboarding
     * state resolves, so there's no blank gap between the splash and the first real screen. */
    private var keepSplashOnScreen = true

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { keepSplashOnScreen }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startDestination.value = resolveStartDestination(intent)
        startMoodValue.value = resolveMoodValue(intent)
        // A per-app locale switch (Settings language toggle) triggers an Activity recreate that
        // re-delivers this same held Intent to onCreate — without clearing the extra here, the
        // mood-value sheet would re-open every time the user switches language after opening the
        // app from a reflection notification action.
        intent?.removeExtra(EXTRA_MOOD_VALUE)
        setContent {
            AffirmityTheme {
                AffirmityApp(
                    startDestination = startDestination.value,
                    startMoodValue = startMoodValue.value,
                    onOnboardingStateResolved = { keepSplashOnScreen = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        startDestination.value = resolveStartDestination(intent)
        startMoodValue.value = resolveMoodValue(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreenSizes
@Composable
fun AffirmityApp(
    startDestination: AppDestinations = AppDestinations.AFIRMACIONES,
    startMoodValue: Int? = null,
    onOnboardingStateResolved: () -> Unit = {},
) {
    var currentDestination by rememberSaveable { mutableStateOf(startDestination) }
    var pendingMoodValue by rememberSaveable { mutableStateOf<Int?>(null) }
    // Blocks screenshots/screen-recording/recent-apps thumbnails while the mood screen (free-text
    // personal notes) is visible (docs/security/SECURITY_AUDIT.md F-05). No-op in @Preview, where
    // LocalContext isn't backed by an Activity.
    val moodScreenVisible = currentDestination == AppDestinations.ANIMO
    val activityWindow = (LocalContext.current as? Activity)?.window
    DisposableEffect(moodScreenVisible, activityWindow) {
        if (moodScreenVisible) {
            activityWindow?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            activityWindow?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    LaunchedEffect(startDestination, startMoodValue) {
        currentDestination = startDestination
        if (startMoodValue != null) pendingMoodValue = startMoodValue
    }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // D4: manual re-entry state, distinct from `appState.shouldShowOnboardingGuide` (the one-time
    // auto-arm flag) -- reopening from Settings must never re-arm the auto flag (spec R5.4).
    var showOnboardingGuide by rememberSaveable { mutableStateOf(false) }
    var showNotificationDebug by rememberSaveable { mutableStateOf(false) }
    var showMyAffirmations by rememberSaveable { mutableStateOf(false) }
    var showFavorites by rememberSaveable { mutableStateOf(false) }
    // REQ-5.4: replaces the old single-demo boolean. Holds a MeditationCatalogEntry.id so the
    // guided session route is parameterized on which entry to play, not just whether to show one.
    var selectedMeditationEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    // Confirmed pre-session customization values (spec: meditation-customization) for the
    // currently selected entry. Keyed by selectedMeditationEntryId itself, NOT just remembered
    // bare -- picking a different meditation (which always passes through the id going back to
    // null first, since the catalog only re-renders once no entry is selected) must not leak a
    // previous entry's confirmed config into an unrelated one's session.
    var confirmedMeditationCustomization by remember(selectedMeditationEntryId) {
        mutableStateOf<Map<String, String>?>(null)
    }
    // D8: replaces the old showPaywall boolean -- null means hidden, and "shown without a source"
    // is unrepresentable. Every trigger surface supplies its own PaywallSource (spec §5.3).
    var paywallSource by rememberSaveable { mutableStateOf<PaywallSource?>(null) }
    val appState = rememberAffirmityAppState()
    val context = LocalContext.current
    // Local-only, deliberately outside AffirmityAppState/DataSession (see
    // RoomMeditationCustomizationRepository's doc) -- a per-device knob position, not account data.
    val meditationCustomizationRepository = remember {
        RoomMeditationCustomizationRepository(AffirmityDatabase.getInstance(context).meditationCustomizationDao())
    }
    // Only ever read by the "breathing_affirmations" hybrid entry's own enrichment step below --
    // every other entry never touches this repository (see decideMeditationLaunchStep's doc).
    val catalogAffirmationRepository = remember {
        RoomCatalogAffirmationRepository(AffirmityDatabase.getInstance(context).catalogAffirmationDao())
    }

    // Shared upgrade-CTA routing (design.md D7): signed-out taps route to sign-in, never straight
    // to the paywall -- used by both the group selector sheet's per-row CTA and any other entry
    // point (e.g. AccountSettingsCard) that opens the paywall. D8: partially applied per call site
    // with the surface's PaywallSource so paywall_shown can attribute its trigger correctly.
    val onUpgradeClick: (PaywallSource) -> Unit = { source ->
        if (appState.authState.value is AuthState.SignedIn) {
            paywallSource = source
        } else {
            appState.signIn(context)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    // Explicit in-app lapse notice (design.md D8, spec's "Explicit in-app lapse notice"): a live
    // Pro -> Free entitlement transition shows an indefinite snackbar with a "View plans" action
    // that opens the paywall; dismissing it (either way) acknowledges the notice so it never
    // re-fires for the same transition.
    LaunchedEffect(appState.proLapseNotice.value) {
        if (appState.proLapseNotice.value) {
            snackbarScope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = context.getString(R.string.paywall_snackbar_lapse_message),
                    actionLabel = context.getString(R.string.paywall_snackbar_lapse_action),
                    duration = androidx.compose.material3.SnackbarDuration.Long,
                )
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                    paywallSource = PaywallSource.OTHER
                }
                appState.acknowledgeProLapse()
            }
        }
    }

    // Ad-unlock outcome notice (design §6.3, D8): Earned/Dismissed/Unavailable each get a short
    // snackbar via the same host used by proLapseNotice above. D8 (closed): Earned never
    // auto-launches the meditation session or auto-selects an affirmation group -- it is
    // acknowledgement only. Strings resolved here (composable scope), not via context.getString
    // inside the launched coroutine below, per LocalContextGetResourceValueCall lint.
    val adUnlockEarnedMessage = stringResource(R.string.ad_unlock_earned_message)
    val adUnlockDismissedMessage = stringResource(R.string.ad_unlock_dismissed_message)
    val adUnlockUnavailableMessage = stringResource(R.string.ad_unlock_unavailable_message)
    // Item 9: strings for the mid-flow access-blocked snackbar, resolved here (composable scope)
    // for the same LocalContextGetResourceValueCall reason as the ad-unlock messages above.
    val meditationAccessBlockedMessage = stringResource(R.string.meditation_access_blocked_message)
    val paywallSnackbarLapseAction = stringResource(R.string.paywall_snackbar_lapse_action)
    // Fix item 3: shared by BOTH the mid-flow onAccessBlocked callback below AND the
    // composition-time launch re-check further down -- whichever path first notices the access
    // loss shows the SAME snackbar-with-"see plans"-action, instead of the re-check silently
    // clearing the selection with no feedback.
    val showMeditationAccessBlockedSnackbar: () -> Unit = {
        snackbarScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = meditationAccessBlockedMessage,
                actionLabel = paywallSnackbarLapseAction,
                duration = androidx.compose.material3.SnackbarDuration.Short,
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                paywallSource = PaywallSource.OTHER
            }
        }
    }
    LaunchedEffect(appState.adRequestNotice.value) {
        val notice = appState.adRequestNotice.value ?: return@LaunchedEffect
        snackbarScope.launch {
            snackbarHostState.showSnackbar(
                message = when (notice) {
                    AdRequestNotice.EARNED -> adUnlockEarnedMessage
                    AdRequestNotice.DISMISSED -> adUnlockDismissedMessage
                    AdRequestNotice.UNAVAILABLE -> adUnlockUnavailableMessage
                },
                duration = androidx.compose.material3.SnackbarDuration.Short,
            )
            appState.acknowledgeAdRequestNotice()
        }
    }

    var notificationsPermissionGranted by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { notificationsPermissionGranted = NotificationManagerCompat.from(context).areNotificationsEnabled() }

    LaunchedEffect(Unit) {
        val needsRuntimePrompt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        if (needsRuntimePrompt) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(appState.hasCompletedOnboarding.value) {
        if (appState.hasCompletedOnboarding.value != null) onOnboardingStateResolved()
    }

    if (appState.hasCompletedOnboarding.value == false) {
        OnboardingScreen(
            modifier = Modifier.fillMaxSize(),
            authState = appState.authState.value,
            authError = appState.authError.value,
            onSignInClicked = { appState.signIn(context) },
            onFinished = { appState.completeOnboarding() },
            onCheckReturningAccount = { uid -> appState.hasRemoteOnboardingCompleted(uid) },
        )
        return
    }

    if (appState.hasCompletedOnboarding.value == null) {
        // DataStore hasn't resolved yet — the native splash (Theme.Affirmity.Starting) stays on
        // screen via keepSplashOnScreen until this resolves, so nothing needs to render here.
        return
    }

    val guideGateResolution = resolveGuideGate(
        autoShow = appState.shouldShowOnboardingGuide.value,
        manualShow = showOnboardingGuide,
        healerJustGranted = appState.healerJustGranted.value,
    )

    if (guideGateResolution == GuideGateResolution.AUTO_GUIDE) {
        // R6.1: positioned immediately after the splash branch, before EVERY other overlay gate
        // (including healerJustGranted below) -- this is the continuation of first-run onboarding
        // and must own the first post-survey frame (design D3).
        OnboardingGuideScreen(
            modifier = Modifier.fillMaxSize(),
            onDismiss = remember(appState) {
                { appState.markOnboardingGuideSeen() }
            },
        )
        return
    }

    if (showNotificationDebug) {
        BackHandler { showNotificationDebug = false }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.nav_notification_debug_title)) },
                    navigationIcon = {
                        IconButton(onClick = { showNotificationDebug = false }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.nav_back_content_description)
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            NotificationDebugScreen(
                modifier = Modifier.padding(innerPadding),
                entries = appState.notificationDebugEntries,
                onClear = { appState.clearNotificationDebugLog() },
                onSendTestNotification = { appState.sendTestNotification() },
                onSendTestMoodNotification = { appState.sendTestMoodNotification() },
            )
        }
        return
    }

    if (showMyAffirmations) {
        BackHandler { showMyAffirmations = false }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.progress_affirmations_section_title)) },
                    navigationIcon = {
                        IconButton(onClick = { showMyAffirmations = false }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.nav_back_content_description)
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            MyAffirmationsScreen(
                modifier = Modifier.padding(innerPadding),
                affirmations = appState.affirmations,
                addImageError = appState.addImageError.value,
                importError = appState.importAffirmationsError.value,
                createDecision = appState.customAffirmationCreateDecision,
                onAddAffirmationWithColor = { title, subtitle, colorHex ->
                    appState.addAffirmationWithColor(title, subtitle, colorHex)
                },
                onAddAffirmationWithImage = { title, subtitle, imageUrl ->
                    appState.addAffirmationWithImage(title, subtitle, imageUrl)
                },
                onAddAffirmationWithGalleryImage = { title, subtitle, imageUri ->
                    appState.addAffirmationWithGalleryImage(title, subtitle, imageUri)
                },
                onImportAffirmationsJson = { json, replace ->
                    appState.importAffirmationsFromJson(json, replace)
                },
                onDeleteAffirmation = { id -> appState.removeAffirmation(id) },
                onOverrideCommitted = appState::setTokenOverride,
                onUpgradeClick = { onUpgradeClick(PaywallSource.MY_AFFIRMATIONS) },
                onEvent = appState::logAnalyticsEvent,
            )
        }
        PaywallHost(
            source = paywallSource,
            onDismiss = { paywallSource = null },
            snackbarScope = snackbarScope,
            emit = appState::logAnalyticsEvent,
        )
        return
    }

    if (showFavorites) {
        FavoritesOverlay(
            favorites = appState.favoriteAffirmations,
            onUnfavorite = appState::removeFavorite,
            onDismiss = { showFavorites = false },
        )
        PaywallHost(
            source = paywallSource,
            onDismiss = { paywallSource = null },
            snackbarScope = snackbarScope,
            emit = appState::logAnalyticsEvent,
        )
        return
    }

    val selectedMeditationEntry = resolveSelectedMeditationEntry(selectedMeditationEntryId)
    if (selectedMeditationEntry != null) {
        // Launch-time access re-check (REQ-5.4.1, EC-5): re-resolved at composition time, not
        // reused from the tap-time decision -- a session swap or a live Pro->Free transition
        // between the Discover tap and this composition clears sessionAdUnlocks, and a screen left
        // open across that boundary must not keep playing gated content. Same "validate at action
        // time, never from rendered state" convention AffirmityAppState.activateStreakHealer
        // already documents.
        val isBlocked = isMeditationLaunchBlocked(
            selectedMeditationEntry,
            appState.entitlementTier.value,
            appState.adUnlockState,
            System.currentTimeMillis(),
        )
        if (isBlocked) {
            // Unknown/removed/newly-locked entry (REQ-5.4.3, EC-4): fall through instead of ever
            // composing GuidedMeditationScreen for gated content. If this removes an already
            // composed session, that screen's disposal invokes its shared requestExit path before
            // releasing audio so cancellation bookkeeping is preserved.
            // Fix item 3: this recheck used to clear the selection silently -- now it surfaces the
            // same access-blocked snackbar the onAccessBlocked callback below shows, so the user
            // gets feedback regardless of which path caught the access loss.
            LaunchedEffect(selectedMeditationEntry.id) {
                selectedMeditationEntryId = null
                showMeditationAccessBlockedSnackbar()
            }
        } else {
            // True exactly when this entry's customization screen is actually about to be shown
            // (mirrors decideMeditationLaunchStep's own condition) -- gates the repository read
            // below so a no-fields entry, or one whose customization is already confirmed, never
            // triggers an unnecessary meditationCustomizationDao query, matching pre-extraction
            // behavior (that read used to live entirely inside this branch).
            val needsSavedCustomizationValues =
                selectedMeditationEntry.customizationFields.isNotEmpty() && confirmedMeditationCustomization == null
            // Saved per-device customization values (spec: meditation-customization), fetched
            // async -- starts empty (so decideMeditationLaunchStep resolves pure defaults for the
            // very first frame, same as before this extraction) and is filled in once the
            // LaunchedEffect below resolves. Compose-only timing/coroutine-launch mechanics; the
            // actual decision of what to do with these values is decideMeditationLaunchStep.
            var savedCustomizationValues by remember(selectedMeditationEntry.id) {
                mutableStateOf(emptyMap<String, String>())
            }
            // Item 4 fix: distinguishes "not needed" from "needed but not back yet" -- true
            // immediately when no read is required, so a no-fields (or already-confirmed) entry's
            // StartSession branch below is never blocked on this. When a read IS required, this
            // stays false until the LaunchedEffect resolves, which gates ShowCustomization below
            // from ever composing MeditationCustomizationScreen with defaults it would then forget
            // to update (that screen's `remember(entry.id)` form state only ever initializes once).
            var savedCustomizationValuesLoaded by remember(selectedMeditationEntry.id) {
                mutableStateOf(!needsSavedCustomizationValues)
            }
            LaunchedEffect(selectedMeditationEntry.id, needsSavedCustomizationValues) {
                if (needsSavedCustomizationValues) {
                    savedCustomizationValues = meditationCustomizationRepository.getValues(selectedMeditationEntry.id)
                    savedCustomizationValuesLoaded = true
                }
            }
            val launchStep = decideMeditationLaunchStep(
                entry = selectedMeditationEntry,
                confirmedCustomization = confirmedMeditationCustomization,
                savedValues = savedCustomizationValues,
            )
            when (launchStep) {
                is MeditationLaunchStep.ShowCustomization -> {
                    if (!savedCustomizationValuesLoaded) {
                        // Same loading-placeholder shape as the StartSession branch's
                        // `sessionCustomization == null` case below -- show nothing editable until
                        // the real saved values are back, instead of composing the form with
                        // defaults it can never pick the real values up from afterward.
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        return
                    }
                    // Pre-session customization step. Sits strictly after the access re-check
                    // above -- an entry never reaches this branch unless it's already unlocked --
                    // and strictly before GuidedMeditationScreen ever calls entry.definition(...),
                    // so no session can start with an unconfirmed/unsaved config.
                    MeditationCustomizationScreen(
                        entry = selectedMeditationEntry,
                        initialValues = launchStep.seedValues,
                        onStart = { values ->
                            snackbarScope.launch {
                                meditationCustomizationRepository.saveValues(selectedMeditationEntry.id, values)
                            }
                            confirmedMeditationCustomization = values
                        },
                        onCancel = { selectedMeditationEntryId = null },
                    )
                }
                is MeditationLaunchStep.StartSession -> {
                    // Special-cased async enrichment for the ONE entry whose config needs runtime
                    // content a synchronous entry.definition(...) call can't fetch itself (see
                    // BREATHING_AFFIRMATIONS_ENTRY_ID's doc). Every other entry: this block resolves
                    // synchronously to launchStep.customization, unchanged, on the very first frame --
                    // no loading state, no repository read, identical to pre-enrichment behavior.
                    val needsAffirmationContent = selectedMeditationEntry.id == BREATHING_AFFIRMATIONS_ENTRY_ID
                    var resolvedSessionCustomization by remember(selectedMeditationEntry.id, launchStep) {
                        mutableStateOf(if (needsAffirmationContent) null else launchStep.customization)
                    }
                    LaunchedEffect(selectedMeditationEntry.id, launchStep) {
                        if (needsAffirmationContent) {
                            val affirmationTexts = affirmationTextsForBreathingAffirmations(
                                universe = launchStep.customization["affirmationUniverse"] ?: "adaptive",
                                count = launchStep.customization["affirmationCount"]?.toIntOrNull() ?: 5,
                                affirmationRepository = catalogAffirmationRepository,
                            )
                            resolvedSessionCustomization = launchStep.customization +
                                affirmationTexts.mapIndexed { index, text -> "affirmationText.$index" to text }
                        }
                    }
                    val sessionCustomization = resolvedSessionCustomization
                    if (sessionCustomization == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        return
                    }
                    val backDispatcherOwner = LocalOnBackPressedDispatcherOwner.current
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            TopAppBar(
                                title = { Text(stringResource(selectedMeditationEntry.titleRes)) },
                                navigationIcon = {
                                    // Single back path (REQ-5.4.2): GuidedMeditationScreen owns the
                                    // one BackHandler for this route -- it must dispatch
                                    // MeditationEvent.Cancel and emit onSessionEnded before exiting.
                                    // This icon triggers that SAME registered callback via the
                                    // system back dispatcher instead of reimplementing the exit
                                    // logic here, so there is exactly one back path, not two.
                                    IconButton(
                                        onClick = {
                                            backDispatcherOwner?.onBackPressedDispatcher?.onBackPressed()
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = stringResource(R.string.nav_back_content_description)
                                        )
                                    }
                                }
                            )
                        }
                    ) { innerPadding ->
                        GuidedMeditationScreen(
                            entry = selectedMeditationEntry,
                            modifier = Modifier.padding(innerPadding),
                            customization = sessionCustomization,
                            accessAtStart = {
                                meditationAccessDecision(
                                    selectedMeditationEntry,
                                    appState.entitlementTier.value,
                                    appState.adUnlockState,
                                    System.currentTimeMillis(),
                                )
                            },
                            // Item 9 fix: a mid-flow access-check failure used to silently clear the
                            // selection and bounce back to the catalog with zero feedback. Surfaces
                            // the same snackbar host used elsewhere in this file (proLapseNotice,
                            // adRequestNotice above), with an action into the paywall so the user
                            // isn't just told "no" -- they're told what to do about it.
                            onAccessBlocked = {
                                selectedMeditationEntryId = null
                                showMeditationAccessBlockedSnackbar()
                            },
                            // The streak-bug fix (REQ-5.6): guided completion now calls the same
                            // recordMeditationCompleted() the free timer already calls at its own
                            // call site. consumeMeditationPlaybackUnlock runs for BOTH terminal
                            // reasons (an ad watched and then abandoned mid-session is still a
                            // use); recordMeditationCompleted only for Completed.
                            onSessionEnded = { reason, elapsedSeconds, startWallMillis ->
                                handleGuidedMeditationSessionEnded(
                                    entryId = selectedMeditationEntry.id,
                                    reason = reason,
                                    elapsedSeconds = elapsedSeconds,
                                    accessDecision = meditationAccessDecision(
                                        selectedMeditationEntry,
                                        appState.entitlementTier.value,
                                        appState.adUnlockState,
                                        System.currentTimeMillis(),
                                    ),
                                    consumePlaybackUnlock = appState::consumeMeditationPlaybackUnlock,
                                    recordMeditationCompleted = { appState.recordMeditationCompleted(startWallMillis) },
                                    emit = appState::logAnalyticsEvent,
                                )
                            },
                            onExit = { selectedMeditationEntryId = null },
                            onEvent = appState::logAnalyticsEvent,
                        )
                    }
                }
            }
        }
        return
    }

    if (showSettings) {
        BackHandler { showSettings = false }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_title)) },
                    navigationIcon = {
                        IconButton(onClick = { showSettings = false }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.nav_back_content_description)
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            SettingsScreen(
                modifier = Modifier.padding(innerPadding),
                reminderSettings = appState.reminderSettings.value,
                reflectionSettings = appState.reflectionSettings.value,
                moodSettings = appState.moodSettings.value,
                quietHoursSettings = appState.quietHoursSettings.value,
                notificationsPermissionGranted = notificationsPermissionGranted,
                authState = appState.authState.value,
                syncError = appState.syncError.value,
                onSignInClicked = { appState.signIn(context) },
                onSignOutClicked = { appState.signOut() },
                onReminderEnabledChanged = { enabled ->
                    appState.setChannelEnabled(
                        NotificationChannelSpec.REMINDER,
                        enabled
                    )
                },
                onReminderSegmentsChanged = { segments ->
                    appState.setChannelSegments(
                        NotificationChannelSpec.REMINDER,
                        segments
                    )
                },
                onReflectionEnabledChanged = { enabled ->
                    appState.setChannelEnabled(
                        NotificationChannelSpec.REFLECTION,
                        enabled
                    )
                },
                onReflectionSegmentsChanged = { segments ->
                    appState.setChannelSegments(
                        NotificationChannelSpec.REFLECTION,
                        segments
                    )
                },
                onMoodEnabledChanged = { enabled ->
                    appState.setChannelEnabled(
                        NotificationChannelSpec.MOOD,
                        enabled
                    )
                },
                onMoodSegmentsChanged = { segments ->
                    appState.setChannelSegments(
                        NotificationChannelSpec.MOOD,
                        segments
                    )
                },
                onQuietHoursEnabledChanged = { enabled ->
                    appState.setQuietHoursEnabled(enabled)
                },
                onQuietHoursWindowChanged = { start, end ->
                    appState.setQuietHoursWindow(start, end)
                },
                onOpenNotificationDebug = { showNotificationDebug = true },
                tier = appState.entitlementTier.value,
                onUpgradeClick = { onUpgradeClick(PaywallSource.SETTINGS) },
                onManageSubscriptionClick = {
                    // Play has no in-app cancel/downgrade API -- routes to Play Store's own
                    // subscription management surface (design.md Phase 7).
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(
                            "https://play.google.com/store/account/subscriptions" +
                                "?sku=$PRO_SUBSCRIPTION_PRODUCT_ID&package=${context.packageName}",
                        ),
                    )
                    context.startActivity(intent)
                },
                onOpenOnboardingGuide = {
                    showOnboardingGuide = true
                    showSettings = false
                },
            )
        }
        return
    }

    if (guideGateResolution == GuideGateResolution.MANUAL_GUIDE) {
        // R6.3/D4: manual re-entry, orthogonal to the auto-show flag -- always opens at slide 1
        // (R5.2) and never re-arms/flips the DataStore guide-seen state on close, except as a
        // safety net if it was somehow still unseen (R5.3, handled by markOnboardingGuideSeen()
        // always writing `true`).
        OnboardingGuideScreen(
            modifier = Modifier.fillMaxSize(),
            onDismiss = remember(appState) {
                {
                    showOnboardingGuide = false
                    appState.markOnboardingGuideSeen()
                }
            },
        )
        return
    }

    if (guideGateResolution == GuideGateResolution.HEALER_GRANTED) {
        BackHandler { appState.acknowledgeHealerGrant() }
        StreakHealerGrantedScreen(
            modifier = Modifier.fillMaxSize(),
            onConfirm = { appState.acknowledgeHealerGrant() },
        )
        return
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            imageVector = it.icon,
                            contentDescription = stringResource(it.labelRes)
                        )
                    },
                    label = { Text(stringResource(it.labelRes)) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (currentDestination) {
                AppDestinations.AFIRMACIONES -> {
                    // Docked, always-visible bottom sheet (feedback: the redesign must keep this
                    // pattern, not open via a separate entry-point icon) -- same
                    // BottomSheetScaffold/peek-row shape as the old group-selector sheet, now
                    // hosting YourFeedSheetContent instead of the old flat group list.
                    var openSurfaceId by remember { mutableStateOf<String?>(null) }
                    var showSeeAllThemes by remember { mutableStateOf(false) }
                    val recommendedSurfaces = remember { defaultRecommendedSurfaces() }
                    val accessDecisionFor: (String) -> AccessDecision = { themeId ->
                        themeAccessDecision(
                            themeId,
                            appState.entitlementTier.value,
                            appState.adUnlockState,
                            System.currentTimeMillis(),
                        )
                    }

                    val yourFeedSheetState = rememberStandardBottomSheetState(
                        initialValue = SheetValue.PartiallyExpanded,
                        skipHiddenState = true,
                        confirmValueChange = { target ->
                            target != SheetValue.PartiallyExpanded || appState.isDraftThemeSelectionValid
                        },
                    )
                    val yourFeedScaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = yourFeedSheetState)
                    val yourFeedScope = rememberCoroutineScope()

                    BottomSheetScaffold(
                        scaffoldState = yourFeedScaffoldState,
                        sheetPeekHeight = 64.dp,
                        sheetDragHandle = null,
                        sheetContent = {
                            YourFeedSheetContent(
                                isExpanded = yourFeedSheetState.currentValue == SheetValue.Expanded,
                                draftThemeIds = appState.draftThemeIds.value,
                                isDirty = appState.draftThemeIds.value != appState.selectedThemeIds.value,
                                isValid = appState.isDraftThemeSelectionValid,
                                catalogThemesById = catalogThemesById(),
                                recommendedSurfaces = recommendedSurfaces,
                                accessDecisionFor = accessDecisionFor,
                                onRemoveTheme = { themeId -> appState.toggleTheme(themeId, toggleable = true) },
                                onOpenSurface = { surfaceId -> openSurfaceId = surfaceId },
                                onSeeAllThemes = { showSeeAllThemes = true },
                                onUpdateFeed = {
                                    if (appState.applyThemeSelection()) {
                                        yourFeedScope.launch { yourFeedSheetState.partialExpand() }
                                    }
                                },
                                onDone = {
                                    appState.resetThemeDraftToCommitted()
                                    yourFeedScope.launch { yourFeedSheetState.partialExpand() }
                                },
                                onPeekClick = {
                                    yourFeedScope.launch {
                                        appState.resetThemeDraftToCommitted()
                                        yourFeedSheetState.expand()
                                    }
                                },
                                onFavoritesClick = { showFavorites = true },
                            )
                        },
                    ) {
                        AffirmationsScreen(
                            affirmations = appState.filteredAffirmations,
                            onAffirmationViewed = { appState.recordAffirmationViewed() },
                            onOverrideCommitted = appState::setTokenOverride,
                            favoriteIds = appState.favoriteAffirmationIds.value,
                            onToggleFavorite = appState::toggleFavorite,
                        )
                    }

                    val openSurface = openSurfaceId?.let { id -> recommendedSurfaces.firstOrNull { it.id == id } }
                    if (openSurface != null) {
                        val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                        SurfaceDetailBottomSheet(
                            surface = openSurface,
                            draftThemeIds = appState.draftThemeIds.value,
                            accessDecisionFor = accessDecisionFor,
                            onToggleTheme = { themeId ->
                                appState.toggleTheme(themeId, toggleable = isThemeToggleable(accessDecisionFor(themeId)))
                            },
                            onUpgradeClick = { onUpgradeClick(PaywallSource.GROUP_SELECTOR) },
                            onEvent = appState::logAnalyticsEvent,
                            onDismiss = { openSurfaceId = null },
                            sheetState = detailSheetState,
                        )
                    }

                    if (showSeeAllThemes) {
                        val seeAllSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                        ModalBottomSheet(
                            onDismissRequest = { showSeeAllThemes = false },
                            sheetState = seeAllSheetState,
                        ) {
                            SeeAllThemesScreen(
                                draftThemeIds = appState.draftThemeIds.value,
                                accessDecisionFor = accessDecisionFor,
                                onToggleTheme = { themeId ->
                                    appState.toggleTheme(themeId, toggleable = isThemeToggleable(accessDecisionFor(themeId)))
                                },
                                onUpgradeClick = { onUpgradeClick(PaywallSource.GROUP_SELECTOR) },
                                onEvent = appState::logAnalyticsEvent,
                                onAddCustomClick = { showMyAffirmations = true },
                                onClose = { showSeeAllThemes = false },
                            )
                        }
                    }
                }

                AppDestinations.MEDITAR -> MeditationScreen(
                    initialDurationSeconds = appState.meditationDurationSeconds.value ?: (15 * 60),
                    onDurationSelected = { seconds -> appState.recordMeditationDurationSelected(seconds) },
                    onSessionCompleted = { durationSeconds, startWallMillis ->
                        appState.recordMeditationCompleted(startWallMillis)
                        appState.logAnalyticsEvent(AnalyticsEvent.FreeTimerCompleted(durationSeconds))
                    },
                    entries = meditationCatalog(),
                    decisionFor = { entry ->
                        meditationAccessDecision(
                            entry,
                            appState.entitlementTier.value,
                            appState.adUnlockState,
                            System.currentTimeMillis(),
                        )
                    },
                    onLaunch = { entry -> selectedMeditationEntryId = entry.id },
                    onUpgradeClick = { onUpgradeClick(PaywallSource.MEDITATION_CATALOG) },
                    onWatchAd = { entry, policy ->
                        appState.requestAdUnlock(meditationContentKey(entry.id), policy)
                    },
                    adInFlightFor = { entry ->
                        appState.adRequestInFlight.value == meditationContentKey(entry.id)
                    },
                    anyAdInFlight = appState.adRequestInFlight.value != null,
                    onEvent = appState::logAnalyticsEvent,
                )

                AppDestinations.PROGRESO -> ProgressScreen(
                    affirmationsStreak = appState.affirmationsStreak.value,
                    meditationStreak = appState.meditationStreak.value,
                    streakHealer = appState.streakHealer.value,
                    onActivateHealer = { appState.activateStreakHealer() },
                )

                AppDestinations.ANIMO -> MoodScreen(
                    moodEntries = appState.moodEntries,
                    onSaveMood = { epochDay, moodValue, note -> appState.recordMood(epochDay, moodValue, note) },
                    initialMoodValue = pendingMoodValue,
                    onInitialMoodConsumed = { pendingMoodValue = null },
                )
            }

            val showsRacha = currentDestination == AppDestinations.AFIRMACIONES ||
                currentDestination == AppDestinations.MEDITAR
            if (showsRacha ||
                currentDestination == AppDestinations.ANIMO ||
                currentDestination == AppDestinations.PROGRESO
            ) {
                FloatingStatusOverlay(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 16.dp),
                    photoUrl = (appState.authState.value as? AuthState.SignedIn)?.photoUrl,
                    generalStreakDays = appState.streakHealer.value.generalStreakDays,
                    isTodayDone = appState.streakHealer.value.isTodayDone,
                    onAvatarClick = { showSettings = true },
                    showStreak = showsRacha,
                    onStreakClick = { currentDestination = AppDestinations.PROGRESO },
                )
            }
            }
        }
    }

    PaywallHost(
        source = paywallSource,
        onDismiss = { paywallSource = null },
        snackbarScope = snackbarScope,
        emit = appState::logAnalyticsEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesOverlay(
    favorites: List<Affirmation>,
    onUnfavorite: (affirmationId: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.favorites_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back_content_description),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        FavoritesScreen(
            favorites = favorites,
            onUnfavorite = onUnfavorite,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

/** Extracted from the bottom of [AffirmityApp] (D5): the original `if (showPaywall)` block lived
 *  after the My Affirmations branch's early `return`, making the paywall unreachable from that
 *  screen. Behavior at the original call site is byte-identical; this host is now also called from
 *  the My Affirmations branch so its `onUpgradeClick` (shared, unchanged) actually opens something.
 *
 *  D8: [source] replaces the old `visible: Boolean` -- null means hidden. `paywall_shown` fires
 *  inside `LaunchedEffect(source)`, NEVER in the composable body: this composable recomposes on
 *  every `monthlyOffer`/`annualOffer` emission, so a body-level emit would multi-count. */
@Composable
private fun PaywallHost(
    source: PaywallSource?,
    onDismiss: () -> Unit,
    snackbarScope: CoroutineScope,
    emit: (AnalyticsEvent) -> Unit = {},
) {
    if (source == null) return
    val context = LocalContext.current
    val activity = context as? Activity
    val billingService = remember {
        BillingService(
            context = context.applicationContext,
            proSubscriptionProductId = PRO_SUBSCRIPTION_PRODUCT_ID,
            syncEntitlementUrl = SYNC_ENTITLEMENT_URL,
            scope = snackbarScope,
        )
    }
    DisposableEffect(billingService) {
        billingService.connect()
        onDispose { billingService.disconnect() }
    }
    val monthlyOffer by billingService.monthlyOffer.collectAsState()
    val annualOffer by billingService.annualOffer.collectAsState()

    LaunchedEffect(source) {
        emit(AnalyticsEvent.PaywallShown(source, access = null))
    }

    PaywallSheet(
        monthlyPriceLabel = monthlyOffer?.formattedPrice,
        annualPriceLabel = annualOffer?.formattedPrice,
        onSelectMonthly = {
            emit(AnalyticsEvent.PaywallPlanSelected(PaywallPlan.MONTHLY, source))
            val offer = monthlyOffer ?: return@PaywallSheet
            activity?.let { billingService.launchPurchaseFlow(it, offer.offerToken) }
        },
        onSelectAnnual = {
            emit(AnalyticsEvent.PaywallPlanSelected(PaywallPlan.ANNUAL, source))
            val offer = annualOffer ?: return@PaywallSheet
            activity?.let { billingService.launchPurchaseFlow(it, offer.offerToken) }
        },
        onDismiss = {
            emit(AnalyticsEvent.PaywallDismissed(source))
            onDismiss()
        },
    )
}

/** Play Console product/base-plan id -- part of the Phase 0 user-owned prerequisite (Play Console
 * subscription setup); placeholder until that product exists. */
private const val PRO_SUBSCRIPTION_PRODUCT_ID = "pro"

/** Deployed `syncEntitlement` Cloud Function URL -- part of the Phase 0 user-owned prerequisite
 * (service account + function deploy); placeholder until that deployment exists. */
private const val SYNC_ENTITLEMENT_URL = ""

enum class AppDestinations(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    AFIRMACIONES(R.string.nav_affirmations_label, Icons.Filled.AutoAwesome),
    MEDITAR(R.string.nav_meditate_label, Icons.Filled.Timer),
    ANIMO(R.string.nav_mood_label, Icons.Filled.Mood),
    PROGRESO(R.string.nav_progress_label, Icons.Filled.Person),
}
