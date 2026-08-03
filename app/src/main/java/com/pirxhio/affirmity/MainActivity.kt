package com.pirxhio.affirmity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.pirxhio.affirmity.data.rememberAffirmityAppState
import com.pirxhio.affirmity.notifications.NotificationChannelSpec
import com.pirxhio.affirmity.ui.affirmations.AffirmationsScreen
import com.pirxhio.affirmity.ui.meditation.MeditationScreen
import com.pirxhio.affirmity.ui.onboarding.OnboardingScreen
import com.pirxhio.affirmity.ui.progress.ProgressScreen
import com.pirxhio.affirmity.ui.settings.NotificationDebugScreen
import com.pirxhio.affirmity.ui.settings.SettingsScreen
import com.pirxhio.affirmity.ui.theme.AffirmityTheme

/** Extra key a launcher (e.g. the home-screen widget) sets to pick the initial [AppDestinations]. */
const val EXTRA_START_DESTINATION = "start_destination"

/** Unknown or absent values fall back to [AppDestinations.AFIRMACIONES] (D10). */
private fun resolveStartDestination(intent: Intent?): AppDestinations {
    val raw = intent?.getStringExtra(EXTRA_START_DESTINATION)
    return AppDestinations.entries.find { it.name == raw } ?: AppDestinations.AFIRMACIONES
}

class MainActivity : ComponentActivity() {
    private val startDestination = mutableStateOf(AppDestinations.AFIRMACIONES)

    /** Keeps the native cold-start splash (Theme.Affirmity.Starting) on screen until onboarding
     * state resolves, so there's no blank gap between the splash and the first real screen. */
    private var keepSplashOnScreen = true

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { keepSplashOnScreen }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startDestination.value = resolveStartDestination(intent)
        setContent {
            AffirmityTheme {
                AffirmityApp(
                    startDestination = startDestination.value,
                    onOnboardingStateResolved = { keepSplashOnScreen = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        startDestination.value = resolveStartDestination(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreenSizes
@Composable
fun AffirmityApp(
    startDestination: AppDestinations = AppDestinations.AFIRMACIONES,
    onOnboardingStateResolved: () -> Unit = {},
) {
    var currentDestination by rememberSaveable { mutableStateOf(startDestination) }
    LaunchedEffect(startDestination) {
        currentDestination = startDestination
    }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showNotificationDebug by rememberSaveable { mutableStateOf(false) }
    val appState = rememberAffirmityAppState()
    val context = LocalContext.current

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

    if (showNotificationDebug) {
        BackHandler { showNotificationDebug = false }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("Debug de notificaciones") },
                    navigationIcon = {
                        IconButton(onClick = { showNotificationDebug = false }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver"
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
            )
        }
        return
    }

    if (showSettings) {
        BackHandler { showSettings = false }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("Ajustes") },
                    navigationIcon = {
                        IconButton(onClick = { showSettings = false }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver"
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
                notificationsPermissionGranted = notificationsPermissionGranted,
                authState = appState.authState.value,
                authError = appState.authError.value,
                syncError = appState.syncError.value,
                onSignInClicked = { appState.signIn(context) },
                onSignOutClicked = { appState.signOut() },
                onReminderEnabledChanged = { enabled ->
                    appState.setChannelEnabled(
                        NotificationChannelSpec.REMINDER,
                        enabled
                    )
                },
                onReminderWindowChanged = { start, end ->
                    appState.setChannelWindow(
                        NotificationChannelSpec.REMINDER,
                        start,
                        end
                    )
                },
                onReflectionEnabledChanged = { enabled ->
                    appState.setChannelEnabled(
                        NotificationChannelSpec.REFLECTION,
                        enabled
                    )
                },
                onReflectionWindowChanged = { start, end ->
                    appState.setChannelWindow(
                        NotificationChannelSpec.REFLECTION,
                        start,
                        end
                    )
                },
                onOpenNotificationDebug = { showNotificationDebug = true },
            )
        }
        return
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            imageVector = it.icon,
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (currentDestination) {
                AppDestinations.AFIRMACIONES -> AffirmationsScreen(
                    affirmations = appState.affirmations,
                    onAffirmationViewed = { appState.recordAffirmationViewed() }
                )

                AppDestinations.MEDITAR -> MeditationScreen(
                    initialDurationSeconds = appState.meditationDurationSeconds.value ?: (15 * 60),
                    onDurationSelected = { seconds -> appState.recordMeditationDurationSelected(seconds) },
                    onSessionCompleted = { appState.recordMeditationCompleted() }
                )

                AppDestinations.PROGRESO -> ProgressScreen(
                    affirmations = appState.affirmations,
                    affirmationsStreak = appState.affirmationsStreak.value,
                    meditationStreak = appState.meditationStreak.value,
                    addImageError = appState.addImageError.value,
                    importError = appState.importAffirmationsError.value,
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
                    onOpenSettings = { showSettings = true }
                )
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    AFIRMACIONES("Afirmaciones", Icons.Filled.AutoAwesome),
    MEDITAR("Meditar", Icons.Filled.Timer),
    PROGRESO("Progreso", Icons.Filled.Person),
}
