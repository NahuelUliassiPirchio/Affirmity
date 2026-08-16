package com.pirxhio.affirmity.ui.meditation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.meditation.CompositeCommandExecutor
import com.pirxhio.affirmity.meditation.LapTrackerCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.MeditationRuntimeState
import com.pirxhio.affirmity.meditation.RealSessionClock
import com.pirxhio.affirmity.meditation.SessionStatus
import com.pirxhio.affirmity.meditation.TextDisplayCommandExecutor
import com.pirxhio.affirmity.meditation.TimerCommandExecutor
import com.pirxhio.affirmity.meditation.breathing.BreathingAudio
import com.pirxhio.affirmity.meditation.breathing.BreathingConfig
import com.pirxhio.affirmity.meditation.breathing.BreathingText
import com.pirxhio.affirmity.meditation.breathing.breathingMeditationDefinition

/**
 * First screen built on the guided-meditation engine (`com.pirxhio.affirmity.meditation`) — wires
 * a [MeditationEngine] to a [RealSessionClock] and a small set of [com.pirxhio.affirmity.
 * meditation.MeditationCommandExecutor]s (timer, text, laps, audio), following the same
 * manual-wiring-in-`remember` convention [com.pirxhio.affirmity.data.rememberAffirmityAppState]
 * uses, but scoped to this screen: session state is ephemeral, not persisted app state.
 */
@Composable
fun GuidedMeditationScreen(
    modifier: Modifier = Modifier,
    config: BreathingConfig = BreathingConfig(),
    onSessionCompleted: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val definition = remember(config) { breathingMeditationDefinition(config) }
    val textExecutor = remember(definition) { TextDisplayCommandExecutor() }

    // The engine and audioExecutor/TimerCommandExecutor need each other before either exists —
    // resolved via a lateinit closed over by their sendEvent lambdas, only actually invoked once
    // the clock/MediaPlayer emits its first event, by which point engineRef is assigned. Both
    // executors are therefore built inside this single remember block, alongside the engine.
    val (audioExecutor, engine) = remember(definition) {
        lateinit var engineRef: MeditationEngine
        val audio = GuidedMeditationAudioExecutor(
            context = context.applicationContext,
            resourcesByAudioId = mapOf(BreathingAudio.RETENTION_START to R.raw.meditation_gong),
            scope = scope,
            timeSource = AndroidMonotonicTimeSource,
            sendEvent = { event -> engineRef.send(event) },
        )
        val clock = RealSessionClock(scope = scope, timeSource = AndroidMonotonicTimeSource)
        val timerExecutor = TimerCommandExecutor(
            clock = clock,
            sendEvent = { event -> engineRef.send(event) },
            scope = scope,
        )
        val commandExecutor = CompositeCommandExecutor(
            listOf(timerExecutor, textExecutor, LapTrackerCommandExecutor(AndroidMonotonicTimeSource), audio),
        )
        engineRef = MeditationEngine(definition, commandExecutor)
        audio to engineRef
    }

    DisposableEffect(audioExecutor) {
        onDispose { audioExecutor.release() }
    }

    val state by engine.state.collectAsState()
    val currentTextId by textExecutor.currentTextId.collectAsState()

    LaunchedEffect(state.status) {
        if (state.status == SessionStatus.Completed) onSessionCompleted()
    }

    GuidedMeditationContent(
        modifier = modifier,
        state = state,
        currentTextId = currentTextId,
        config = config,
        onStart = { engine.send(MeditationEvent.Start) },
        onPause = { engine.send(MeditationEvent.Pause) },
        onResume = { engine.send(MeditationEvent.Resume) },
        onSkip = { engine.send(MeditationEvent.Next) },
        onRelease = { engine.send(MeditationEvent.UserAction()) },
    )
}

@Composable
private fun GuidedMeditationContent(
    modifier: Modifier,
    state: MeditationRuntimeState,
    currentTextId: String?,
    config: BreathingConfig,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit,
    onRelease: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.guided_meditation_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        val roundIndex = state.iterationCounts["rounds"]
        if (roundIndex != null && state.status != SessionStatus.Completed) {
            Text(
                text = stringResource(R.string.guided_meditation_round_label, roundIndex + 1, config.rounds),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        val breathIndex = state.iterationCounts["breathing"]
        if (breathIndex != null && state.status != SessionStatus.Completed) {
            Text(
                text = stringResource(R.string.guided_meditation_breath_label, breathIndex + 1, config.breathsPerRound),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        Box(
            modifier = Modifier
                .padding(top = 32.dp)
                .size(240.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (state.status == SessionStatus.Running || state.status == SessionStatus.Paused) {
                val totalMillis = phaseTotalMillis(state.currentPhaseId, config)
                val progress = if (totalMillis != null && totalMillis > 0) {
                    (state.elapsedInPhaseMillis.toFloat() / totalMillis).coerceIn(0f, 1f)
                } else {
                    0f
                }
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeWidth = 4.dp,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = phaseLabel(currentTextId, state.status),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                val remainingSeconds = state.remainingInPhaseMillis?.let { (it / 1000L).toInt() + 1 }
                val showsRemaining = remainingSeconds != null &&
                    (state.status == SessionStatus.Running || state.status == SessionStatus.Paused)
                if (showsRemaining) {
                    Text(
                        text = "${remainingSeconds}s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        if (state.currentPhaseId == "retention" && state.status == SessionStatus.Running) {
            TextButton(onClick = onRelease, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.guided_meditation_retention_hint))
            }
        }

        Row(
            modifier = Modifier.padding(top = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            when (state.status) {
                SessionStatus.Idle -> {
                    RoundIconButton(
                        onClick = onStart,
                        icon = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.guided_meditation_start_content_description),
                    )
                }

                SessionStatus.Running -> {
                    RoundIconButton(
                        onClick = onPause,
                        icon = Icons.Filled.Pause,
                        contentDescription = stringResource(R.string.guided_meditation_pause_content_description),
                    )
                    RoundIconButton(
                        onClick = onSkip,
                        icon = Icons.Filled.SkipNext,
                        contentDescription = stringResource(R.string.guided_meditation_skip_content_description),
                    )
                }

                SessionStatus.Paused -> {
                    RoundIconButton(
                        onClick = onResume,
                        icon = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.guided_meditation_resume_content_description),
                    )
                    RoundIconButton(
                        onClick = onSkip,
                        icon = Icons.Filled.SkipNext,
                        contentDescription = stringResource(R.string.guided_meditation_skip_content_description),
                    )
                }

                SessionStatus.Completed, SessionStatus.Cancelled -> Unit
            }
        }
    }
}

@Composable
private fun RoundIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(64.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = Color.Black)
    }
}

private fun phaseTotalMillis(phaseId: String?, config: BreathingConfig): Long? = when (phaseId) {
    "inhale" -> config.inhaleMillis
    "exhale" -> config.exhaleMillis
    "retention" -> config.retentionMillis
    "recovery" -> config.recoveryMillis
    else -> null
}

@Composable
private fun phaseLabel(currentTextId: String?, status: SessionStatus): String = when {
    status == SessionStatus.Completed -> stringResource(R.string.guided_meditation_completed)
    currentTextId == BreathingText.INHALE -> stringResource(R.string.guided_meditation_inhale)
    currentTextId == BreathingText.EXHALE -> stringResource(R.string.guided_meditation_exhale)
    currentTextId == BreathingText.RETENTION -> stringResource(R.string.guided_meditation_retention)
    currentTextId == BreathingText.RECOVERY -> stringResource(R.string.guided_meditation_recovery)
    else -> ""
}
