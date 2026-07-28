package com.pirxhio.affirmity

import android.Manifest
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
import com.pirxhio.affirmity.data.rememberAffirmityAppState
import com.pirxhio.affirmity.notifications.NotificationChannelSpec
import com.pirxhio.affirmity.ui.affirmations.AffirmationsScreen
import com.pirxhio.affirmity.ui.meditation.MeditationScreen
import com.pirxhio.affirmity.ui.progress.ProgressScreen
import com.pirxhio.affirmity.ui.settings.SettingsScreen
import com.pirxhio.affirmity.ui.theme.AffirmityTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AffirmityTheme {
                AffirmityApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreenSizes
@Composable
fun AffirmityApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.AFIRMACIONES) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
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
                    onAddAffirmationWithColor = { title, subtitle, colorHex ->
                        appState.addAffirmationWithColor(title, subtitle, colorHex)
                    },
                    onAddAffirmationWithImage = { title, subtitle, imageUrl ->
                        appState.addAffirmationWithImage(title, subtitle, imageUrl)
                    },
                    onAddAffirmationWithGalleryImage = { title, subtitle, imageUri ->
                        appState.addAffirmationWithGalleryImage(title, subtitle, imageUri)
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
