package com.pirxhio.affirmity.ui.meditation

import android.media.MediaPlayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val MIN_DURATION_SECONDS = 30
private const val MAX_DURATION_SECONDS = 30 * 60
private const val STEP_SECONDS = 30

// Height of the discover list intentionally left peeking above the fold, so the user notices
// there's more content below without it competing with the timer for attention.
private val DISCOVER_LIST_PEEK_HEIGHT = 96.dp

@Composable
fun MeditationScreen(
    initialDurationSeconds: Int = 15 * 60,
    onDurationSelected: (Int) -> Unit,
    onSessionCompleted: () -> Unit,
    onOpenGuidedDemo: () -> Unit = {},
) {
    // Keyed on initialDurationSeconds so that when the persisted value arrives asynchronously
    // (DataStore's first read completes after this composable's initial composition), the
    // screen picks it up instead of being stuck on the fallback default.
    var durationSeconds by remember(initialDurationSeconds) { mutableIntStateOf(initialDurationSeconds) }
    var secondsRemaining by remember(initialDurationSeconds) { mutableIntStateOf(durationSeconds) }
    var isRunning by remember { mutableStateOf(false) }
    var selectedPreset by remember { mutableStateOf<String?>(null) }
    val presets = listOf(
        stringResource(R.string.meditation_preset_relax) to 5 * 60,
        stringResource(R.string.meditation_preset_focus) to 15 * 60,
        stringResource(R.string.meditation_preset_sleep) to 30 * 60,
    )

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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = viewportHeight - DISCOVER_LIST_PEEK_HEIGHT)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.meditation_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.meditation_subtitle),
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
                                contentDescription = if (isRunning) {
                                    stringResource(R.string.meditation_pause_content_description)
                                } else {
                                    stringResource(R.string.meditation_start_content_description)
                                },
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
                        Text(stringResource(R.string.meditation_max_duration_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    }
                    Slider(
                        value = durationSeconds.toFloat(),
                        onValueChange = {
                            // Round to the nearest step, not floor: truncating toInt() always rounds
                            // down, so a value like 6:00 could only ever be reached from below, never
                            // from a finger landing anywhere in its upper half.
                            durationSeconds = (it / STEP_SECONDS).roundToInt() * STEP_SECONDS
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

            MeditationDiscoverSection(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp)
            )

            TextButton(
                onClick = onOpenGuidedDemo,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp, bottom = 24.dp)
            ) {
                Text(stringResource(R.string.meditation_guided_demo_button))
            }
        }
    }
}

private data class MockMeditationTrack(
    val icon: ImageVector,
    val titleRes: Int,
    val metaRes: Int,
)

private val mockDiscoverTracks = listOf(
    MockMeditationTrack(Icons.Filled.WaterDrop, R.string.meditation_discover_track_1_title, R.string.meditation_discover_track_1_meta),
    MockMeditationTrack(Icons.Filled.Bookmark, R.string.meditation_discover_track_2_title, R.string.meditation_discover_track_2_meta),
    MockMeditationTrack(Icons.Filled.SelfImprovement, R.string.meditation_discover_track_3_title, R.string.meditation_discover_track_3_meta),
)

/** Mock discovery list, styled after the Stitch "Meditación: Descubrimiento Minimalista"
 * reference — kept deliberately small and left peeking under the timer via
 * [DISCOVER_LIST_PEEK_HEIGHT] so the user notices there's more to scroll to. */
@Composable
private fun MeditationDiscoverSection(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.meditation_discover_header),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            mockDiscoverTracks.forEach { track ->
                MeditationDiscoverCard(track)
            }
        }
    }
}

@Composable
private fun MeditationDiscoverCard(track: MockMeditationTrack) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = track.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(track.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = stringResource(track.metaRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1
                )
            }
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

private fun formatTime(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}
