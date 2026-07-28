package com.pirxhio.affirmity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import com.pirxhio.affirmity.data.rememberAffirmityAppState
import com.pirxhio.affirmity.ui.affirmations.AffirmationsScreen
import com.pirxhio.affirmity.ui.meditation.MeditationScreen
import com.pirxhio.affirmity.ui.progress.ProgressScreen
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

@PreviewScreenSizes
@Composable
fun AffirmityApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.AFIRMACIONES) }
    val appState = rememberAffirmityAppState()

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
                    onAddAffirmation = { title, subtitle, colorHex ->
                        appState.addAffirmation(title, subtitle, colorHex)
                    },
                    onDeleteAffirmation = { id -> appState.removeAffirmation(id) }
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
