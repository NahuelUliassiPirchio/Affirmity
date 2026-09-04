package com.pirxhio.affirmity.ui.meditation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.BuildConfig
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.access.AccessDecision
import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.ads.BannerAdView
import com.pirxhio.affirmity.analytics.AnalyticsEvent
import com.pirxhio.affirmity.analytics.AnalyticsId
import com.pirxhio.affirmity.analytics.provenance
import com.pirxhio.affirmity.data.DayClock
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
import com.pirxhio.affirmity.ui.meditation.catalog.isMeditationLocked

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
    /** Entitlement read once at screen entry (design D1, resolved — no default). A missing
     * wire-up at the call site must be a compile error, not a silent fall-through to showing (or
     * hiding) the free-tier banner ad. */
    tierAtEntry: () -> AccessTier,
    modifier: Modifier = Modifier,
    /** Confirmed values from the pre-session customization screen, keyed by field id. Populated
     * per-entry based on the meditation's spec-defined customizable fields; empty only for
     * entries with no [MeditationCatalogEntry.customizationFields]. */
    customization: Map<String, String> = emptyMap(),
    /** Re-resolved by the caller when Start is pressed, so time-bound access cannot expire while
     * this screen sits idle and still start gated content. The returned decision also tags
     * [AnalyticsEvent.MeditationStarted]'s `access_decision` parameter. */
    accessAtStart: () -> AccessDecision = { AccessDecision.Unlocked },
    /** Routes an action-time denial through the caller's existing blocked-screen path. */
    onAccessBlocked: () -> Unit = {},
    /** Fired exactly once per playback session that reaches a terminal state, carrying the
     * UI-local wall-clock elapsed duration (design D7 -- the engine tracks no session-wide
     * elapsed) and the wall-clock instant the session started (for day-of-completion attribution
     * across a local-midnight crossing, see [DayClock.attributedEpochDay]). Never fired when the
     * screen is disposed from [SessionStatus.Idle] (EC-1) — a user who never pressed Start has not
     * spent anything. */
    onSessionEnded: (SessionEndReason, elapsedSeconds: Long, startWallMillis: Long) -> Unit = { _, _, _ -> },
    /** Called after any cancellation bookkeeping, to leave the screen. Owned by the caller. */
    onExit: () -> Unit = {},
    /** Spec 6 emit surface (REQ-5.2) -- fires `meditation_started` at the Start dispatch below. */
    onEvent: (AnalyticsEvent) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // D1 (resolved, no default): frozen for the composition lifetime, exactly like accessAtStart
    // -- a mid-session tier change must never make the banner appear/disappear mid-meditation.
    val showBannerAd = remember { shouldShowMeditationBanner(tierAtEntry()) }

    // D7: UI-local wall clock, captured at the Start dispatch below and diffed at both terminal
    // paths (Completed LaunchedEffect, Cancelled exit). Measures wall time including pauses --
    // the honest engagement number, and zero engine change.
    var sessionStartMillis by remember { mutableStateOf<Long?>(null) }
    // Wall-clock (System.currentTimeMillis()), captured alongside sessionStartMillis but never used
    // for duration math -- only to attribute the session to a calendar day (DayClock.
    // attributedEpochDay), which the monotonic elapsedRealtime-based sessionStartMillis can't do.
    var sessionStartWallMillis by remember { mutableStateOf<Long?>(null) }
    fun elapsedSecondsSinceStart(): Long {
        val start = sessionStartMillis ?: return 0L
        return ((AndroidMonotonicTimeSource.nowMillis() - start) / 1000L).coerceAtLeast(0L)
    }

    val definition = remember(entry, customization) { entry.definition(customization) }
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

    val state by engine.state.collectAsState()
    val currentTextId by textExecutor.currentTextId.collectAsState()
    val currentLiteralText by textExecutor.currentLiteralText.collectAsState()
    var exitHandled by remember { mutableStateOf(false) }

    // Site A (REQ-5.2): extends the existing status-driven effect in place, fires exactly on
    // reaching Completed.
    LaunchedEffect(state.status) {
        if (state.status == SessionStatus.Completed) {
            onSessionEnded(SessionEndReason.Completed, elapsedSecondsSinceStart(), sessionStartWallMillis ?: System.currentTimeMillis())
        }
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
        if (!exitHandled) {
            exitHandled = true
            performGuidedSessionExit(
                status = engine.state.value.status,
                cancel = { engine.send(MeditationEvent.Cancel) },
                onSessionEnded = { reason ->
                    onSessionEnded(reason, elapsedSecondsSinceStart(), sessionStartWallMillis ?: System.currentTimeMillis())
                },
                onExit = onExit,
            )
        }
    }
    val currentRequestExit = rememberUpdatedState(requestExit)

    // Parent-driven route removal (including access expiry) must use the same cancellation and
    // bookkeeping path as explicit Back before native audio resources are released. Explicit exit
    // sets exitHandled first, so disposal cannot double-report that session.
    DisposableEffect(audioExecutor) {
        onDispose {
            try {
                currentRequestExit.value()
            } finally {
                audioExecutor.release()
            }
        }
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
        currentLiteralText = currentLiteralText,
        entry = entry,
        customization = customization,
        phaseDurations = phaseDurations,
        showBannerAd = showBannerAd,
        onStart = {
            val currentAccess = accessAtStart()
            if (isMeditationLocked(currentAccess)) {
                onAccessBlocked()
            } else {
                sessionStartMillis = AndroidMonotonicTimeSource.nowMillis()
                sessionStartWallMillis = System.currentTimeMillis()
                onEvent(AnalyticsEvent.MeditationStarted(AnalyticsId.of(entry), currentAccess.provenance()))
                engine.send(MeditationEvent.Start)
            }
        },
        onPause = { engine.send(MeditationEvent.Pause) },
        onResume = { engine.send(MeditationEvent.Resume) },
        onSkip = { engine.send(MeditationEvent.Next) },
        onRelease = { engine.send(MeditationEvent.UserAction()) },
        // Item 13: session-complete had no visible exit affordance -- wired to the same single
        // exit path (`requestExit`) the BackHandler above and MainActivity's top-bar back icon
        // already dispatch through, so there is still exactly one exit implementation.
        onDone = requestExit,
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
    currentLiteralText: String?,
    entry: MeditationCatalogEntry,
    customization: Map<String, String>,
    phaseDurations: Map<String, Long>,
    showBannerAd: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit,
    onRelease: () -> Unit,
    onDone: () -> Unit,
) {
    // D3: Column -> Box so the banner can occupy a fixed BottomCenter slot outside the scrollable
    // content, with the inner Column's bottom padding reserving exactly the banner's measured
    // height (0.dp while pending/failed, ~50.dp once loaded) instead of overlapping the controls.
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        var bannerHeight by remember { mutableStateOf(0.dp) }
        val density = LocalDensity.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp + bannerHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
        entry.presentation.counters.forEach { counter ->
            val index = state.iterationCounts[counter.repeatId]
            if (index != null && state.status != SessionStatus.Completed) {
                // Item 10 fix: resolves the actual customized total (e.g. Dhikr's chosen
                // repetitions) when the counter declares which customization key to read, instead
                // of always showing the catalog-authored `total` (33) regardless of what the user
                // picked on the pre-session customization screen.
                val resolvedTotal = counter.totalFromCustomizationKey
                    ?.let { customization[it]?.toIntOrNull() }
                    ?: counter.total
                Text(
                    text = stringResource(counter.labelRes, index + 1, resolvedTotal),
                    style = when (counter.emphasis) {
                        CounterEmphasis.PRIMARY -> MaterialTheme.typography.bodyMedium
                        CounterEmphasis.SECONDARY -> MaterialTheme.typography.bodySmall
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            if (state.status == SessionStatus.Idle) {
                // Calmer pre-play treatment: a soft breathing-ring backdrop instead of the empty
                // space this box used to show before Start was tapped. Purely decorative --
                // Running/Paused keep their own CircularProgressIndicator branch below untouched.
                IdleBreathingBackdrop(modifier = Modifier.fillMaxSize())
            } else if (state.status == SessionStatus.Running || state.status == SessionStatus.Paused) {
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

            if (state.status == SessionStatus.Idle) {
                // Real content instead of a void: the meditation's own title, and its duration
                // when one can be resolved, so this screen reads as a pre-session summary rather
                // than a blank stage waiting for the play button.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(entry.titleRes),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    val durationMinutes = idleDurationMinutes(phaseDurations, entry.approxDurationMinutes)
                    if (durationMinutes != null) {
                        Text(
                            text = stringResource(R.string.guided_meditation_idle_duration_minutes, durationMinutes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = phaseLabel(currentTextId, currentLiteralText, state.status, entry.presentation.textResources),
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
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

                // Item 13 fix: Completed previously showed no button at all -- the user had to
                // guess system Back was the way out. Cancelled still shows nothing: that state is
                // never actually observed here (performGuidedSessionExit calls onExit in the same
                // frame Cancel is dispatched, unmounting this composable before a Cancelled frame
                // could render), so there's no reachable state to add a button for.
                SessionStatus.Completed -> {
                    Button(onClick = onDone) {
                        Text(stringResource(R.string.guided_meditation_done))
                    }
                }
                SessionStatus.Cancelled -> Unit
            }
        }
        }

        // D2: last, unconditional child -- never inside an `if (state.status ...)` branch and
        // never `key()`ed, so its Compose identity (and the LaunchedEffect's one-shot delay+load)
        // survives every status transition without restarting.
        if (showBannerAd) {
            BannerAdView(
                adUnitId = BuildConfig.ADMOB_BANNER_UNIT,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { bannerHeight = with(density) { it.height.toDp() } },
            )
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
        Icon(imageVector = icon, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/**
 * Best-effort session length for the Idle summary, in whole minutes. Prefers the sum of this
 * entry's actually-customized [phaseDurations] (Fixed-duration phases only, per
 * [com.pirxhio.affirmity.ui.meditation.catalog.fixedPhaseDurationsById]) rounded up, so a
 * duration/rounds customization the user just picked on the previous screen is reflected here.
 * Falls back to the catalog-declared [approxDurationMinutes] when no phase in this entry has a
 * Fixed duration (e.g. an entry driven entirely by manual release or variable-length phases),
 * and is never null in practice since every entry declares an approximate duration.
 */
private fun idleDurationMinutes(phaseDurations: Map<String, Long>, approxDurationMinutes: Int): Int? {
    val fixedTotalMillis = phaseDurations.values.sum()
    if (fixedTotalMillis <= 0L) return approxDurationMinutes.takeIf { it > 0 }
    val wholeMinutesRoundedUp = ((fixedTotalMillis + 59_999L) / 60_000L).toInt()
    return wholeMinutesRoundedUp.coerceAtLeast(1)
}

/**
 * Soft, non-literal pre-play backdrop for [SessionStatus.Idle]: three concentric rings fading
 * outward from the brand teal, evoking a breathing motif without drawing custom illustration
 * assets (none exist in this app -- see craft constraints). Purely decorative -- draws behind the
 * title/duration text and the play button sits just below this box, never on top of it.
 */
@Composable
private fun IdleBreathingBackdrop(modifier: Modifier = Modifier) {
    val ringColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val maxRadius = size.minDimension / 2f
        val ringSpecs = listOf(1f to 0.05f, 0.74f to 0.09f, 0.5f to 0.14f)
        ringSpecs.forEach { (radiusFraction, alpha) ->
            drawCircle(
                color = ringColor.copy(alpha = alpha),
                radius = maxRadius * radiusFraction,
                center = center,
            )
        }
    }
}

@Composable
private fun phaseLabel(
    currentTextId: String?,
    currentLiteralText: String?,
    status: SessionStatus,
    textResources: Map<String, Int>,
): String = when {
    // Terminal copy stays global, not per-entry: it is a screen state, not content.
    status == SessionStatus.Completed -> stringResource(R.string.guided_meditation_completed)
    currentLiteralText != null -> currentLiteralText
    else -> currentTextId?.let { textResources[it] }?.let { stringResource(it) } ?: ""
}
