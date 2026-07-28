package com.pirxhio.affirmity.ui.meditation

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import kotlinx.coroutines.delay

private const val MIN_DURATION_SECONDS = 30
private const val MAX_DURATION_SECONDS = 30 * 60
private const val STEP_SECONDS = 30

private val presets = listOf("Relax" to 5 * 60, "Focus" to 15 * 60, "Sleep" to 30 * 60)

@Composable
fun MeditationScreen(
    initialDurationSeconds: Int = 15 * 60,
    onDurationSelected: (Int) -> Unit,
    onSessionCompleted: () -> Unit,
) {
    // Keyed on initialDurationSeconds so that when the persisted value arrives asynchronously
    // (DataStore's first read completes after this composable's initial composition), the
    // screen picks it up instead of being stuck on the fallback default.
    var durationSeconds by remember(initialDurationSeconds) { mutableIntStateOf(initialDurationSeconds) }
    var secondsRemaining by remember(initialDurationSeconds) { mutableIntStateOf(durationSeconds) }
    var isRunning by remember { mutableStateOf(false) }
    var selectedPreset by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val gongPlayer = remember { MediaPlayer.create(context, R.raw.meditation_gong) }
    DisposableEffect(Unit) {
        onDispose { gongPlayer.release() }
    }

    // Real countdown driven by a coroutine delay loop (not a fake animation).
    LaunchedEffect(isRunning) {
        while (isRunning && secondsRemaining > 0) {
            delay(1000)
            secondsRemaining -= 1
        }
        if (isRunning && secondsRemaining <= 0) {
            isRunning = false
            gongPlayer.seekTo(0)
            gongPlayer.start()
            secondsRemaining = durationSeconds
            onSessionCompleted()
        }
    }

    val totalSeconds = durationSeconds.coerceAtLeast(1)
    val progress = 1f - (secondsRemaining.toFloat() / totalSeconds)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Meditar",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Encuentra tu centro.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 4.dp, bottom = 40.dp)
        )

        Box(
            modifier = Modifier.size(280.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.primaryContainer,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeWidth = 4.dp,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatTime(secondsRemaining),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                IconButton(
                    onClick = { isRunning = !isRunning },
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isRunning) "Pausar" else "Iniciar",
                        tint = Color.Black
                    )
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(top = 48.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(MIN_DURATION_SECONDS), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                Text("30 min", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            }
            Slider(
                value = durationSeconds.toFloat(),
                onValueChange = {
                    durationSeconds = (it / STEP_SECONDS).toInt() * STEP_SECONDS
                    secondsRemaining = durationSeconds
                },
                onValueChangeFinished = { onDurationSelected(durationSeconds) },
                valueRange = MIN_DURATION_SECONDS.toFloat()..MAX_DURATION_SECONDS.toFloat(),
                // one stop every 30s between the bounds, exclusive of both ends
                steps = (MAX_DURATION_SECONDS - MIN_DURATION_SECONDS) / STEP_SECONDS - 1,
                enabled = !isRunning,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primaryContainer,
                    activeTrackColor = MaterialTheme.colorScheme.primaryContainer,
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                presets.forEach { (label, seconds) ->
                    FilterChip(
                        selected = selectedPreset == label,
                        onClick = {
                            if (!isRunning) {
                                selectedPreset = label
                                durationSeconds = seconds
                                secondsRemaining = durationSeconds
                                onDurationSelected(durationSeconds)
                            }
                        },
                        label = { Text(label) },
                        enabled = !isRunning
                    )
                }
            }
        }
    }
}

private fun formatTime(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}
