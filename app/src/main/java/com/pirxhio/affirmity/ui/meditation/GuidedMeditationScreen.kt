package com.pirxhio.affirmity.ui.meditation

import androidx.activity.compose.BackHandler
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
import com.pirxhio.affirmity.meditation.SessionEndReason
import com.pirxhio.affirmity.meditation.SessionStatus
import com.pirxhio.affirmity.meditation.TextDisplayCommandExecutor
import com.pirxhio.affirmity.meditation.TimerCommandExecutor
import com.pirxhio.affirmity.ui.meditation.catalog.CounterEmphasis
import com.pirxhio.affirmity.ui.meditation.catalog.MeditationCatalogEntry
import com.pirxhio.affirmity.ui.meditation.catalog.fixedPhaseDurationsById

/**
 * First screen built on the guided-meditation engine (`com.pirxhio.affirmity.meditation`) — wires
 * a [MeditationEngine] to a [RealSessionClock] and a small set of [com.pirxhio.affirmity.
 * meditation.MeditationCommandExecutor]s (timer, text, laps, audio), following the same
 * manual-wiring-in-`remember` convention [com.pirxhio.affirmity.data.rememberAffirmityAppState]
 * uses, but scoped to this screen: session state is ephemeral, not persisted app state.
 *
 * Generic over [MeditationCatalogEntry] (REQ-5.1) — the screen holds no knowledge of any specific
 * meditation; every entry-specific value (definition, audio resources, counters, manual-release
 * gate, phase text) is read from [entry].
 */
@Composable
fun GuidedMeditationScreen(
    entry: MeditationCatalogEntry,
    modifier: Modifier = Modifier,
    /** Fired exactly once per playback session that reaches a terminal state. Never fired when the
     * screen is disposed from [SessionStatus.Idle] (EC-1) — a user who never pressed Start has not
     * spent anything. */
    onSessionEnded: (SessionEndReason) -> Unit = {},
    /** Called after any cancellation bookkeeping, to leave the screen. Owned by the caller. */
    onExit: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val definition = remember(entry) { entry.definition() }
    val textExecutor = remember(definition) { TextDisplayCommandExecutor() }
    val phaseDurations = remember(definition) { fixedPhaseDurationsById(definition) }

    // The engine and audioExecutor/TimerCommandExecutor need each other before either exists —
    // resolved via a lateinit closed over by their sendEvent lambdas, only actually invoked once
    // the clock/MediaPlayer emits its first event, by which point engineRef is assigned. Both
    // executors are therefore built inside this single remember block, alongside the engine.
    val (audioExecutor, engine) = remember(definition) {
        lateinit var engineRef: MeditationEngine
        val audio = GuidedMeditationAudioExecutor(
            context = context.applicationContext,
            resourcesByAudioId = entry.presentation.audioResources,
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

    // Site A (REQ-5.2): extends the existing status-driven effect in place, fires exactly on
    // reaching Completed.
    LaunchedEffect(state.status) {
        if (state.status == SessionStatus.Completed) onSessionEnded(SessionEndReason.Completed)
    }

    // Site B (REQ-5.2, EC-2): emitted synchronously from the exit handler rather than from a
    // recomposition-driven effect. Exiting removes this composable in the same frame the exit is
    // requested, so an effect keyed on state.status may never get a chance to run before disposal.
    // engine.send is synchronous (MeditationEngine.send holds a plain monitor and updates
    // _state.value inline), so by the line after engine.send(Cancel) the session is already
    // terminal and safe to report. Idle is deliberately excluded (EC-1): a user who never pressed
    // Start has not spent anything. The ordering/branching itself lives in the extracted
    // `performGuidedSessionExit` below so it can be exercised by a plain JUnit test against a real
    // MeditationEngine (verify REQ-5.2/AC7) without a Compose test harness.
    val requestExit: () -> Unit = {
        performGuidedSessionExit(
            status = state.status,
            cancel = { engine.send(MeditationEvent.Cancel) },
            onSessionEnded = onSessionEnded,
            onExit = onExit,
        )
    }

    // Single back path (REQ-5.4.2): this screen is the ONLY BackHandler owner for the guided
    // session route. A caller-side back affordance (e.g. a TopAppBar navigationIcon) MUST trigger
    // this same registered callback via the system back dispatcher rather than reimplementing the
    // exit logic — a second independent implementation would risk skipping the Cancel dispatch and
    // silently drop PER_USE consumption.
    BackHandler(onBack = requestExit)

    GuidedMeditationContent(
        modifier = modifier,
        state = state,
        currentTextId = currentTextId,
        entry = entry,
        phaseDurations = phaseDurations,
        onStart = { engine.send(MeditationEvent.Start) },
        onPause = { engine.send(MeditationEvent.Pause) },
        onResume = { engine.send(MeditationEvent.Resume) },
        onSkip = { engine.send(MeditationEvent.Next) },
        onRelease = { engine.send(MeditationEvent.UserAction()) },
    )
}

/**
 * Pure exit-decision logic for [GuidedMeditationScreen]'s single back/exit path (REQ-5.2, AC7,
 * EC-2). Extracted out of the Composable so it can be driven by a plain JUnit test against a real
 * [MeditationEngine] — proving [cancel] (which synchronously drives the engine to
 * [SessionStatus.Cancelled]) runs, and [onSessionEnded] is invoked with
 * [SessionEndReason.Cancelled], strictly before [onExit] — without needing a Compose test
 * harness. [status] is a snapshot read by the caller at call time (mirrors the composable's own
 * `state.status` read); [cancel] is expected to synchronously dispatch [MeditationEvent.Cancel].
 */
internal fun performGuidedSessionExit(
    status: SessionStatus,
    cancel: () -> Unit,
    onSessionEnded: (SessionEndReason) -> Unit,
    onExit: () -> Unit,
) {
    if (status == SessionStatus.Running || status == SessionStatus.Paused) {
        cancel()
        onSessionEnded(SessionEndReason.Cancelled)
    }
    onExit()
}

@Composable
private fun GuidedMeditationContent(
    modifier: Modifier,
    state: MeditationRuntimeState,
    currentTextId: String?,
    entry: MeditationCatalogEntry,
    phaseDurations: Map<String, Long>,
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
        entry.presentation.counters.forEach { counter ->
            val index = state.iterationCounts[counter.repeatId]
            if (index != null && state.status != SessionStatus.Completed) {
                Text(
                    text = stringResource(counter.labelRes, index + 1, counter.total),
                    style = when (counter.emphasis) {
                        CounterEmphasis.PRIMARY -> MaterialTheme.typography.bodyMedium
                        CounterEmphasis.SECONDARY -> MaterialTheme.typography.bodySmall
                    },
                    color = MaterialTheme.colorScheme.outline,
                    modifier = if (counter.emphasis == CounterEmphasis.PRIMARY) {
                        Modifier.padding(top = 8.dp)
                    } else {
                        Modifier
                    },
                )
            }
        }

        Box(
            modifier = Modifier
                .padding(top = 32.dp)
                .size(240.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (state.status == SessionStatus.Running || state.status == SessionStatus.Paused) {
                val totalMillis = phaseDurations[state.currentPhaseId]
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
                    text = phaseLabel(currentTextId, state.status, entry.presentation.textResources),
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

        val release = entry.presentation.manualRelease?.takeIf { it.phaseId == state.currentPhaseId }
        if (release != null && state.status == SessionStatus.Running) {
            TextButton(onClick = onRelease, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(release.hintRes))
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

@Composable
private fun phaseLabel(
    currentTextId: String?,
    status: SessionStatus,
    textResources: Map<String, Int>,
): String = when {
    // Terminal copy stays global, not per-entry: it is a screen state, not content.
    status == SessionStatus.Completed -> stringResource(R.string.guided_meditation_completed)
    else -> currentTextId?.let { textResources[it] }?.let { stringResource(it) } ?: ""
}
