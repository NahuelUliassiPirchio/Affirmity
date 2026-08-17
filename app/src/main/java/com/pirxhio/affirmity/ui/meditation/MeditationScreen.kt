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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
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
import com.pirxhio.affirmity.ui.groups.AffirmationGroupAccessBadge
import com.pirxhio.affirmity.ui.meditation.catalog.MeditationCatalogEntry
import com.pirxhio.affirmity.ui.meditation.catalog.deriveMeditationBadge
import com.pirxhio.affirmity.ui.meditation.catalog.isMeditationLocked
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
    /** D7: the free timer already knows its own [durationSeconds] -- it simply passes it, kept
     *  structurally distinct from the catalog `meditation_completed` event (REQ-5.2). */
    onSessionCompleted: (durationSeconds: Long) -> Unit,
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
            onSessionCompleted(durationSeconds.toLong())
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
                entries = entries,
                decisionFor = decisionFor,
                onLaunch = onLaunch,
                onUpgradeClick = onUpgradeClick,
                onWatchAd = onWatchAd,
                adInFlightFor = adInFlightFor,
                anyAdInFlight = anyAdInFlight,
                onEvent = onEvent,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp)
            )
        }
    }
}

/** Real Discover list (REQ-5.3): renders every shipping [MeditationCatalogEntry], each row's
 * lock state resolved via [decisionFor] at composition time. Left peeking under the timer via
 * [DISCOVER_LIST_PEEK_HEIGHT] so the user notices there's more to scroll to. No [androidx.compose
 * .foundation.lazy.LazyColumn]: this section nests inside the screen's own unbounded-height
 * `verticalScroll` (design §4.3) — a nested LazyColumn there would crash. */
@Composable
fun MeditationDiscoverSection(
    entries: List<MeditationCatalogEntry>,
    decisionFor: (MeditationCatalogEntry) -> AccessDecision,
    onLaunch: (MeditationCatalogEntry) -> Unit,
    onUpgradeClick: () -> Unit,
    onWatchAd: (MeditationCatalogEntry, AdUnlockPolicy) -> Unit,
    adInFlightFor: (MeditationCatalogEntry) -> Boolean = { false },
    anyAdInFlight: Boolean = false,
    onEvent: (AnalyticsEvent) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.meditation_discover_header),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.forEach { entry ->
                MeditationDiscoverCard(
                    entry = entry,
                    decision = decisionFor(entry),
                    onLaunch = onLaunch,
                    onUpgradeClick = onUpgradeClick,
                    onWatchAd = onWatchAd,
                    adInFlight = adInFlightFor(entry),
                    anyAdInFlight = anyAdInFlight,
                    onEvent = onEvent,
                )
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
    // Three interaction branches (REQ-5.3/design §4.2), mirroring AffirmationGroupSelectableRow.
    // An ad-unlockable row is inert while ANY ad request is in flight (§6.1) -- the tap is
    // dropped, not queued -- defense in depth over AffirmityAppState's single-flight guard.
    val onRowClick: () -> Unit = when (decision) {
        is AccessDecision.Unlocked, is AccessDecision.UnlockedByAd -> {
            {
                onEvent(AnalyticsEvent.MeditationEntryTapped(AnalyticsId.of(entry), decision.provenance(), entry.access.adUnlock))
                onLaunch(entry)
            }
        }
        is AccessDecision.LockedAdUnlockable ->
            if (anyAdInFlight) { {} } else { { onWatchAd(entry, decision.policy) } }
        AccessDecision.LockedNeedsPro -> onLockedTap
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRowClick)
            .let { if (locked) it.alpha(0.6f) else it },
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
                    imageVector = entry.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
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
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1
                )
                if (badge != null) {
                    AffirmationGroupAccessBadge(badge)
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
                IconButton(onClick = onLockedTap) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = stringResource(
                            R.string.affirmation_group_locked_a11y,
                            stringResource(entry.titleRes),
                        ),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
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

private fun formatTime(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}
