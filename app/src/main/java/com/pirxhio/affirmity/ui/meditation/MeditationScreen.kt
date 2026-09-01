package com.pirxhio.affirmity.ui.meditation

import android.media.MediaPlayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.access.AccessDecision
import com.pirxhio.affirmity.access.AdUnlockPolicy
import com.pirxhio.affirmity.analytics.AnalyticsContentType
import com.pirxhio.affirmity.analytics.AnalyticsEvent
import com.pirxhio.affirmity.analytics.AnalyticsId
import com.pirxhio.affirmity.analytics.provenance
import com.pirxhio.affirmity.data.DayClock
import com.pirxhio.affirmity.meditation.ClockEvent
import com.pirxhio.affirmity.meditation.RealSessionClock
import com.pirxhio.affirmity.ui.groups.AffirmationGroupAccessBadge
import com.pirxhio.affirmity.ui.meditation.catalog.MeditationCatalogEntry
import com.pirxhio.affirmity.ui.meditation.catalog.deriveMeditationBadge
import com.pirxhio.affirmity.ui.meditation.catalog.isMeditationLocked
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
    /** D7: the free timer already knows its own [durationSeconds] -- it simply passes it, kept
     *  structurally distinct from the catalog `meditation_completed` event (REQ-5.2).
     *  [startWallMillis] is the wall-clock instant Start was pressed, for day-of-completion
     *  attribution across a local-midnight crossing (see [DayClock.attributedEpochDay]). */
    onSessionCompleted: (durationSeconds: Long, startWallMillis: Long) -> Unit,
    entries: List<MeditationCatalogEntry>,
    decisionFor: (MeditationCatalogEntry) -> AccessDecision,
    onLaunch: (MeditationCatalogEntry) -> Unit,
    onUpgradeClick: () -> Unit,
    onWatchAd: (MeditationCatalogEntry, AdUnlockPolicy) -> Unit,
    adInFlightFor: (MeditationCatalogEntry) -> Boolean = { false },
    anyAdInFlight: Boolean = false,
    /** Spec 6 emit surface (REQ-5.2/5.4) -- fires `meditation_entry_tapped` and
     *  `content_locked_tapped` from the Discover list. */
    onEvent: (AnalyticsEvent) -> Unit = {},
) {
    var durationSeconds by remember { mutableIntStateOf(initialDurationSeconds) }
    var secondsRemaining by remember { mutableIntStateOf(initialDurationSeconds) }
    var isRunning by remember { mutableStateOf(false) }
    // Tracks first-start vs. resume-from-pause, since RealSessionClock.start() resets the
    // accumulated elapsed time while resume() continues it.
    var hasStarted by remember { mutableStateOf(false) }
    // Wall-clock instant of the first Start press, for DayClock.attributedEpochDay -- not touched
    // on resume, only on a fresh start (mirrors hasStarted).
    var sessionStartWallMillis by remember { mutableStateOf<Long?>(null) }
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

    val scope = rememberCoroutineScope()
    val clock = remember { RealSessionClock(scope = scope, timeSource = AndroidMonotonicTimeSource) }

    // Pick up the asynchronously hydrated DataStore value only while no session is active. Once
    // Start has been pressed, the duration and all state tied to this clock must remain one
    // coherent session, including while paused.
    LaunchedEffect(initialDurationSeconds) {
        if (!hasStarted) {
            durationSeconds = initialDurationSeconds
            secondsRemaining = initialDurationSeconds
        }
    }

    // Timestamp-based, not tick-counted: while the phone is locked, Android throttles this
    // coroutine's scheduling (Doze, no wake lock held), so a "delay(1000); secondsRemaining -= 1"
    // loop only counts the iterations that actually got to run and drifts behind real time.
    // RealSessionClock instead recomputes remainingMillis from elapsedRealtime() on every tick, so
    // it's always correct the moment ticking resumes, no matter how long it was stalled.
    LaunchedEffect(Unit) {
        clock.events.collect { event ->
            when (event) {
                is ClockEvent.Tick -> event.remainingMillis?.let { secondsRemaining = (it / 1000L).toInt() }
                ClockEvent.Completed -> {
                    isRunning = false
                    hasStarted = false
                    gongPlayer.seekTo(0)
                    gongPlayer.start()
                    secondsRemaining = durationSeconds
                    onSessionCompleted(durationSeconds.toLong(), sessionStartWallMillis ?: System.currentTimeMillis())
                }
            }
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            onClick = {
                                if (isRunning) {
                                    clock.pause()
                                    isRunning = false
                                } else {
                                    if (hasStarted) {
                                        clock.resume()
                                    } else {
                                        clock.start(durationSeconds * 1000L)
                                        sessionStartWallMillis = System.currentTimeMillis()
                                    }
                                    hasStarted = true
                                    isRunning = true
                                }
                            },
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
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxWidth().padding(top = 48.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(MIN_DURATION_SECONDS), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.meditation_max_duration_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Slider(
                        value = durationSeconds.toFloat(),
                        onValueChange = {
                            // Round to the nearest step, not floor: truncating toInt() always rounds
                            // down, so a value like 6:00 could only ever be reached from below, never
                            // from a finger landing anywhere in its upper half.
                            durationSeconds = (it / STEP_SECONDS).roundToInt() * STEP_SECONDS
                            secondsRemaining = durationSeconds
                            hasStarted = false
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
                                // Item 14: derived straight from `durationSeconds` instead of a
                                // separately tracked `selectedPreset` -- dragging the slider off a
                                // preset's value now un-highlights every chip on its own, instead
                                // of leaving a stale chip highlighted while the slider disagrees.
                                selected = durationSeconds == seconds,
                                onClick = {
                                    if (!isRunning) {
                                        durationSeconds = seconds
                                        secondsRemaining = durationSeconds
                                        hasStarted = false
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

            // Item 11: was a plain eager Column over all ~39 catalog entries, nested inside the
            // screen's own unbounded-height `verticalScroll` -- didn't scale and (per the removed
            // doc comment here) couldn't become a LazyColumn while nested like that anyway. Now
            // this whole screen is ONE LazyColumn (the timer/duration block above is its `item {}`
            // header), so the Discover list can lazily load via `items(...)` below without nesting.
            item {
                Text(
                    text = stringResource(R.string.meditation_discover_header),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 12.dp)
                )
            }
            items(entries, key = { it.id }) { entry ->
                MeditationDiscoverCard(
                    entry = entry,
                    decision = decisionFor(entry),
                    onLaunch = onLaunch,
                    onUpgradeClick = onUpgradeClick,
                    onWatchAd = onWatchAd,
                    adInFlight = adInFlightFor(entry),
                    anyAdInFlight = anyAdInFlight,
                    onEvent = onEvent,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                )
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun MeditationDiscoverCard(
    entry: MeditationCatalogEntry,
    decision: AccessDecision,
    onLaunch: (MeditationCatalogEntry) -> Unit,
    onUpgradeClick: () -> Unit,
    onWatchAd: (MeditationCatalogEntry, AdUnlockPolicy) -> Unit,
    adInFlight: Boolean = false,
    anyAdInFlight: Boolean = false,
    onEvent: (AnalyticsEvent) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val locked = isMeditationLocked(decision)
    val badge = deriveMeditationBadge(entry, decision)
    val adUnlockLoadingA11y = stringResource(R.string.ad_unlock_loading_a11y)
    // REQ-5.4: one shared local, used by BOTH the LockedNeedsPro row branch and the trailing lock
    // IconButton below -- two affordances, one event, no double-count.
    val onLockedTap: () -> Unit = {
        onEvent(AnalyticsEvent.ContentLockedTapped(AnalyticsId.of(entry), AnalyticsContentType.MEDITATION, decision.provenance()))
        onUpgradeClick()
    }
    // Fix (contrast-audit item 8): a LockedAdUnlockable row no longer fires onWatchAd from a tap
    // anywhere on the row -- only the explicit "Watch ad to unlock" CTA below does. The row itself
    // has no clickable at all in this state (fix item 2: a no-op clickable still produced a ripple
    // and a misleading TalkBack-announced action for a tap that did nothing).
    val onRowClick: (() -> Unit)? = when (decision) {
        is AccessDecision.Unlocked, is AccessDecision.UnlockedByAd -> {
            {
                onEvent(AnalyticsEvent.MeditationEntryTapped(AnalyticsId.of(entry), decision.provenance(), entry.access.adUnlock))
                onLaunch(entry)
            }
        }
        is AccessDecision.LockedAdUnlockable -> null
        AccessDecision.LockedNeedsPro -> onLockedTap
    }
    val onWatchAdTap: () -> Unit = {
        if (decision is AccessDecision.LockedAdUnlockable && !anyAdInFlight) {
            onWatchAd(entry, decision.policy)
        }
    }
    // Item 12: while ANY ad request is in flight, every OTHER ad-unlockable card must read as
    // non-interactive rather than silently swallowing taps -- the one place a blanket dim is
    // correct (it's a transient loading state), unlike the generic `locked` case below (item 3).
    val disabledByOtherAdInFlight = decision is AccessDecision.LockedAdUnlockable && anyAdInFlight && !adInFlight

    Card(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onRowClick != null) it.clickable(onClick = onRowClick, enabled = !disabledByOtherAdInFlight) else it }
            .let { if (disabledByOtherAdInFlight) it.alpha(0.5f) else it },
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
                    // Item 3: only the icon/thumbnail dims to signal "locked" now -- title, meta
                    // and badge below stay at full opacity so they remain legible.
                    .background(
                        if (locked) {
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        RoundedCornerShape(10.dp),
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = null,
                    tint = if (locked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(entry.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = "${entry.approxDurationMinutes} min • ${stringResource(entry.categoryRes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                when {
                    // Item 8: LockedAdUnlockable gets its own explicit "Watch ad to unlock" CTA
                    // instead of the shared PREMIUM/"Pro only" badge -- tapping IT (not the row)
                    // is the only thing that invokes onWatchAd.
                    decision is AccessDecision.LockedAdUnlockable -> WatchAdBadge(
                        onClick = onWatchAdTap,
                        enabled = !anyAdInFlight,
                    )
                    badge != null -> AffirmationGroupAccessBadge(badge)
                }
            }
            if (adInFlight) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(22.dp)
                        .semantics { contentDescription = adUnlockLoadingA11y },
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.outline,
                )
            } else if (locked) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = stringResource(
                        R.string.affirmation_group_locked_a11y,
                        stringResource(entry.titleRes),
                    ),
                    tint = MaterialTheme.colorScheme.outline,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.PlayCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/** Item 8's explicit ad-unlock CTA -- distinct from [AffirmationGroupAccessBadge]'s generic
 *  "Pro only" copy, and the only tap target in [MeditationDiscoverCard] that invokes [onWatchAd]-
 *  bound logic. [enabled] false (another card's ad is in flight) dims it without disabling the
 *  rest of the row, since the card itself already handles that case via `disabledByOtherAdInFlight`. */
@Composable
private fun WatchAdBadge(onClick: () -> Unit, enabled: Boolean) {
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50))
            .clickable(onClick = onClick, enabled = enabled)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.PlayCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = stringResource(R.string.meditation_watch_ad_cta),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

private fun formatTime(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}
