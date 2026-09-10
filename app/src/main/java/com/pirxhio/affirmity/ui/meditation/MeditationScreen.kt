package com.pirxhio.affirmity.ui.meditation

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.pirxhio.affirmity.ui.groups.GroupBadge
import com.pirxhio.affirmity.ui.meditation.catalog.MeditationCatalogEntry
import com.pirxhio.affirmity.ui.meditation.catalog.deriveMeditationBadge
import com.pirxhio.affirmity.ui.meditation.catalog.isMeditationLocked
import com.pirxhio.affirmity.ui.theme.OnPremiumContainerDark
import com.pirxhio.affirmity.ui.theme.OnPremiumContainerLight
import com.pirxhio.affirmity.ui.theme.PremiumContainerDark
import com.pirxhio.affirmity.ui.theme.PremiumContainerLight
import kotlin.math.roundToInt

private const val MIN_DURATION_SECONDS = 30
private const val MAX_DURATION_SECONDS = 30 * 60
private const val STEP_SECONDS = 30
private const val SESSION_CARD_WIDTH_DP = 108

/** A broader shelf that several under-sized categories fold into, so no shelf ever shows fewer
 *  than 3 cards (Item: "agrupar con min 3" -- raised from the original "no single-entry shelf"
 *  bar once two 2-entry shelves still read as thin). A category absent from [categoryShelfGroups]
 *  already has 3+ entries on its own and keeps its own shelf, named after its own `categoryRes`
 *  directly. */
private enum class ShelfGroup(@androidx.annotation.StringRes val labelRes: Int) {
    CALM_SLEEP(R.string.meditation_shelf_calm_sleep),
    MINDFULNESS(R.string.meditation_shelf_mindfulness),
    BODY(R.string.meditation_shelf_body),
    ENERGY_FOCUS(R.string.meditation_shelf_energy_focus),
    COMPASSION(R.string.meditation_shelf_compassion),
    PRAYER_CONTEMPLATION(R.string.meditation_shelf_prayer_contemplation),
    // Reuses the existing "Bienestar" category label rather than a new one -- this group's third
    // member (breathing_affirmations) already carries that category, so the merged shelf is just
    // that category widened to also hold visualización and gratitud.
    WELLBEING(R.string.meditation_catalog_category_bienestar),
}

/** Maps an under-sized category's `categoryRes` to the [ShelfGroup] shelf it should render under
 *  instead. Picked for thematic fit, not just headcount: relajación/calma/sueño are all about
 *  winding down; silencio (zazen's silent sitting) reads as a mindfulness practice; movimiento
 *  (walking meditation) is body-based like cuerpo; energía and enfoque are both short activating
 *  practices; compasión and autocompasión are the same theme turned outward vs. inward; oración
 *  and contemplación are both devotional/contemplative traditions; visualización and gratitud are
 *  reflective "wellbeing" practices alongside the existing bienestar entry. Every category left
 *  out of this map (respiración, mantra) already has 3+ entries and keeps its own shelf. */
private val categoryShelfGroups: Map<Int, ShelfGroup> = mapOf(
    R.string.meditation_catalog_category_relajacion to ShelfGroup.CALM_SLEEP,
    R.string.meditation_catalog_category_calma to ShelfGroup.CALM_SLEEP,
    R.string.meditation_catalog_category_sueno to ShelfGroup.CALM_SLEEP,
    R.string.meditation_catalog_category_mindfulness to ShelfGroup.MINDFULNESS,
    R.string.meditation_catalog_category_silencio to ShelfGroup.MINDFULNESS,
    R.string.meditation_catalog_category_cuerpo to ShelfGroup.BODY,
    R.string.meditation_catalog_category_movimiento to ShelfGroup.BODY,
    R.string.meditation_catalog_category_energia to ShelfGroup.ENERGY_FOCUS,
    R.string.meditation_catalog_category_enfoque to ShelfGroup.ENERGY_FOCUS,
    R.string.meditation_catalog_category_compasion to ShelfGroup.COMPASSION,
    R.string.meditation_catalog_category_autocompasion to ShelfGroup.COMPASSION,
    R.string.meditation_catalog_category_oracion to ShelfGroup.PRAYER_CONTEMPLATION,
    R.string.meditation_catalog_category_contemplacion to ShelfGroup.PRAYER_CONTEMPLATION,
    R.string.meditation_catalog_category_visualizacion to ShelfGroup.WELLBEING,
    R.string.meditation_catalog_category_gratitud to ShelfGroup.WELLBEING,
    R.string.meditation_catalog_category_bienestar to ShelfGroup.WELLBEING,
)

/** Stable shelf identity for grouping/selection -- a merged shelf's key is its [ShelfGroup] name
 *  (shared by every category folded into it); an ungrouped category keys off its own resource id. */
private fun shelfKeyFor(categoryRes: Int): String = categoryShelfGroups[categoryRes]?.name ?: "cat_$categoryRes"

@Composable
private fun shelfLabelFor(categoryRes: Int): String {
    val group = categoryShelfGroups[categoryRes]
    return if (group != null) stringResource(group.labelRes) else stringResource(categoryRes)
}

// Height of the discover list intentionally left peeking above the fold, so the user notices
// there's more content below without it competing with the timer for attention.
private val DISCOVER_LIST_PEEK_HEIGHT = 96.dp

/** One open ad-unlock request (design 5a's `adSheet`) -- captured at tap time from the concrete
 *  [AccessDecision.LockedAdUnlockable] that triggered it, so the sheet never has to re-derive or
 *  race a decision that could change underneath it while open. */
private data class AdUnlockRequest(val entry: MeditationCatalogEntry, val policy: AdUnlockPolicy)

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
    /** Pre-launch audit item #5's "recent meditations" shelf -- already resolved and ordered
     *  most-recent-first by the caller (see [com.pirxhio.affirmity.data.local.TrackerPreferences.
     *  observeRecentMeditationIds]); this screen does no lookup or sorting of its own. */
    recentEntries: List<MeditationCatalogEntry> = emptyList(),
    decisionFor: (MeditationCatalogEntry) -> AccessDecision,
    onLaunch: (MeditationCatalogEntry) -> Unit,
    onUpgradeClick: () -> Unit,
    onWatchAd: (MeditationCatalogEntry, AdUnlockPolicy) -> Unit,
    adInFlightFor: (MeditationCatalogEntry) -> Boolean = { false },
    anyAdInFlight: Boolean = false,
    /** Spec 6 emit surface (REQ-5.2/5.4) -- fires `meditation_entry_tapped` and
     *  `content_locked_tapped` from the Discover shelves. */
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

    // Discover section state (design 5a): which shelf is filtered ("Todo" = null), and which
    // ad-unlockable entry currently has its unlock sheet open.
    var selectedShelfKey by remember { mutableStateOf<String?>(null) }
    var adUnlockRequest by remember { mutableStateOf<AdUnlockRequest?>(null) }

    // Shelves, one per editorial category -- except the single-entry categories folded into a
    // broader [ShelfGroup] (Item: "agrupar algunas categories porque hay muchas que tienen un
    // solo elemento"). groupBy preserves first-encounter order of both the outer map and each
    // inner list, so shelf order and entry order within a shelf stay exactly as catalog-authored.
    // A selected pill narrows this down to that one shelf instead of hiding entries within a
    // shelf (design 5a: "tap a category to filter the shelves").
    val shelves = remember(entries) { entries.groupBy { shelfKeyFor(it.categoryRes) }.toList() }
    val visibleShelves = selectedShelfKey.let { selected ->
        if (selected == null) shelves else shelves.filter { it.first == selected }
    }

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

            // Discover section (design 5a): a header, a horizontally scrollable row of category
            // pills, then one horizontally scrollable "shelf" of cover-art cards per category --
            // replaces the old single vertical wall of ~39 cards.
            item {
                Text(
                    text = stringResource(R.string.meditation_discover_header),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 16.dp)
                )
            }
            item {
                CategoryPillsRow(
                    shelves = shelves,
                    selected = selectedShelfKey,
                    onSelect = { selectedShelfKey = it },
                )
            }
            // Pre-launch audit item #5: only shown against "Todo" -- a recents shelf mixes
            // categories, so showing it while the user has filtered to one specific category would
            // contradict that filter.
            if (recentEntries.isNotEmpty() && selectedShelfKey == null) {
                item {
                    MeditationShelf(
                        label = stringResource(R.string.meditation_shelf_recent),
                        entries = recentEntries,
                        decisionFor = decisionFor,
                        onLaunch = onLaunch,
                        onUpgradeClick = onUpgradeClick,
                        onOpenAdUnlock = { entry, policy -> adUnlockRequest = AdUnlockRequest(entry, policy) },
                        adInFlightFor = adInFlightFor,
                        anyAdInFlight = anyAdInFlight,
                        onEvent = onEvent,
                    )
                }
            }
            items(visibleShelves, key = { it.first }) { (_, categoryEntries) ->
                MeditationShelf(
                    label = shelfLabelFor(categoryEntries.first().categoryRes),
                    entries = categoryEntries,
                    decisionFor = decisionFor,
                    onLaunch = onLaunch,
                    onUpgradeClick = onUpgradeClick,
                    onOpenAdUnlock = { entry, policy -> adUnlockRequest = AdUnlockRequest(entry, policy) },
                    adInFlightFor = adInFlightFor,
                    anyAdInFlight = anyAdInFlight,
                    onEvent = onEvent,
                )
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    adUnlockRequest?.let { request ->
        AdUnlockSheet(
            entry = request.entry,
            policy = request.policy,
            onWatchAd = {
                onWatchAd(request.entry, request.policy)
                adUnlockRequest = null
            },
            onUpgradeClick = {
                adUnlockRequest = null
                onUpgradeClick()
            },
            onDismiss = { adUnlockRequest = null },
        )
    }

}

/** One calming two-tone gradient per category, picked deterministically from [categoryRes] (a
 *  stable resource id) so the same category always renders the same cover-art tile without
 *  needing any drawable/photo assets -- the project ships none today (design 5a's cover art is an
 *  empty `image-slot` the designer couldn't generate photography for either). Kept as a small
 *  hand-picked palette rather than a hash-to-hue formula so every pairing stays on-brand and
 *  legible with a white icon on top, instead of risking a muddy or low-contrast random hue. */
private val categoryGradients = listOf(
    Color(0xFF6EE7DE) to Color(0xFF2F9E97), // teal
    Color(0xFFB4A7F5) to Color(0xFF7C6FD1), // lavender
    Color(0xFFFFC98B) to Color(0xFFF29B4C), // peach
    Color(0xFFA8D8A0) to Color(0xFF5FA85A), // sage
    Color(0xFF8FCBEA) to Color(0xFF4A97C9), // sky
    Color(0xFFF6A9C0) to Color(0xFFDA6E92), // rose
    Color(0xFFE8D9A0) to Color(0xFFC4A94E), // sand
    Color(0xFFB7C4D6) to Color(0xFF7C8CA3), // slate
)

private fun gradientBrushForCategory(categoryRes: Int): Brush {
    val (start, end) = categoryGradients[Math.floorMod(categoryRes, categoryGradients.size)]
    return Brush.linearGradient(listOf(start, end))
}

/** Design 5a's category pill row -- "Todo" (all shelves) plus one pill per shelf (a lonely
 *  category's own name, or its [ShelfGroup] label once merged), scrollable so an arbitrary shelf
 *  count never wraps or gets cramped. */
@Composable
private fun CategoryPillsRow(
    shelves: List<Pair<String, List<MeditationCatalogEntry>>>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    ) {
        item {
            CategoryPill(
                label = stringResource(R.string.meditation_category_all),
                selected = selected == null,
                onClick = { onSelect(null) },
            )
        }
        items(shelves, key = { it.first }) { (shelfKey, shelfEntries) ->
            CategoryPill(
                label = shelfLabelFor(shelfEntries.first().categoryRes),
                selected = selected == shelfKey,
                onClick = { onSelect(shelfKey) },
            )
        }
    }
}

@Composable
private fun CategoryPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = contentColor,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 8.dp),
    )
}

/** One horizontally scrollable row of [entries] under a category label -- design 5a's "shelf",
 *  replacing the old flat vertical list. */
@Composable
private fun MeditationShelf(
    label: String,
    entries: List<MeditationCatalogEntry>,
    decisionFor: (MeditationCatalogEntry) -> AccessDecision,
    onLaunch: (MeditationCatalogEntry) -> Unit,
    onUpgradeClick: () -> Unit,
    onOpenAdUnlock: (MeditationCatalogEntry, AdUnlockPolicy) -> Unit,
    adInFlightFor: (MeditationCatalogEntry) -> Boolean,
    anyAdInFlight: Boolean,
    onEvent: (AnalyticsEvent) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 26.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            items(entries, key = { it.id }) { entry ->
                MeditationSessionCard(
                    entry = entry,
                    decision = decisionFor(entry),
                    onLaunch = onLaunch,
                    onUpgradeClick = onUpgradeClick,
                    onOpenAdUnlock = onOpenAdUnlock,
                    adInFlight = adInFlightFor(entry),
                    anyAdInFlight = anyAdInFlight,
                    onEvent = onEvent,
                )
            }
        }
    }
}

/** Design 5a's session card: a square cover-art tile (a per-category gradient standing in for the
 *  designer's empty `image-slot`, see [gradientBrushForCategory]) with a lock-state badge overlaid
 *  bottom-left, title and duration below. The whole card is one tap target across all three lock
 *  states -- unlike the old row layout, an ad-unlockable card no longer needs a separate inline
 *  CTA, since tapping it opens [AdUnlockSheet] instead. */
@Composable
private fun MeditationSessionCard(
    entry: MeditationCatalogEntry,
    decision: AccessDecision,
    onLaunch: (MeditationCatalogEntry) -> Unit,
    onUpgradeClick: () -> Unit,
    onOpenAdUnlock: (MeditationCatalogEntry, AdUnlockPolicy) -> Unit,
    adInFlight: Boolean,
    anyAdInFlight: Boolean,
    onEvent: (AnalyticsEvent) -> Unit,
) {
    val locked = isMeditationLocked(decision)
    val badge = deriveMeditationBadge(entry, decision)
    val adUnlockLoadingA11y = stringResource(R.string.ad_unlock_loading_a11y)
    // Item 12 carried over from the old row layout: while ANY ad request is in flight, every
    // OTHER ad-unlockable card must read as non-interactive rather than silently swallowing taps.
    val disabledByOtherAdInFlight = decision is AccessDecision.LockedAdUnlockable && anyAdInFlight && !adInFlight
    val onCardClick: () -> Unit = when (decision) {
        is AccessDecision.Unlocked, is AccessDecision.UnlockedByAd -> {
            {
                onEvent(AnalyticsEvent.MeditationEntryTapped(AnalyticsId.of(entry), decision.provenance(), entry.access.adUnlock))
                onLaunch(entry)
            }
        }
        // Item 8 carried over, restyled per design 5a: tapping an ad-unlockable card now opens the
        // unlock sheet instead of exposing a separate inline "Watch ad" CTA.
        is AccessDecision.LockedAdUnlockable -> {
            { onOpenAdUnlock(entry, decision.policy) }
        }
        AccessDecision.LockedNeedsPro -> {
            {
                onEvent(AnalyticsEvent.ContentLockedTapped(AnalyticsId.of(entry), AnalyticsContentType.MEDITATION, decision.provenance()))
                onUpgradeClick()
            }
        }
    }

    Column(
        modifier = Modifier
            .width(SESSION_CARD_WIDTH_DP.dp)
            .alpha(if (disabledByOtherAdInFlight) 0.5f else 1f)
            .clickable(onClick = onCardClick, enabled = !disabledByOtherAdInFlight),
    ) {
        Box(
            modifier = Modifier
                .size(SESSION_CARD_WIDTH_DP.dp)
                // Only the cover art dims to signal "locked" -- title and duration below stay at
                // full opacity so they remain legible (mirrors the old row layout's Item 3 fix).
                .alpha(if (locked) 0.82f else 1f)
                .background(gradientBrushForCategory(entry.categoryRes), RoundedCornerShape(14.dp)),
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(30.dp).align(Alignment.Center),
            )
            if (adInFlight) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(22.dp)
                        .align(Alignment.Center)
                        .semantics { contentDescription = adUnlockLoadingA11y },
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
            }
            if (badge != null) {
                SessionBadge(badge = badge, modifier = Modifier.align(Alignment.BottomStart).padding(5.dp))
            }
        }
        Text(
            text = stringResource(entry.titleRes),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            modifier = Modifier.padding(top = 7.dp),
        )
        Text(
            text = stringResource(R.string.guided_meditation_idle_duration_minutes, entry.approxDurationMinutes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

// Local to this card's badge -- distinct from AffirmationGroupAccessBadge's neutral AD_UNLOCK
// styling (used on the affirmation-group selector, a different surface) so the three lock states
// on a Discover card read as visually distinct at a glance: this violet-leaning pair for
// ad-unlockable, and the shared Premium container pair (below) for Pro-only.
private val AdUnlockContainerLight = Color(0xFFEDE7FF)
private val OnAdUnlockContainerLight = Color(0xFF4B3F91)
private val AdUnlockContainerDark = Color(0xFF3A3163)
private val OnAdUnlockContainerDark = Color(0xFFD8CFFF)

@Composable
private fun SessionBadge(badge: GroupBadge, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val (containerColor, contentColor, label) = when (badge) {
        GroupBadge.AD_UNLOCK -> Triple(
            if (dark) AdUnlockContainerDark else AdUnlockContainerLight,
            if (dark) OnAdUnlockContainerDark else OnAdUnlockContainerLight,
            stringResource(R.string.meditation_badge_ad_unlock),
        )
        // PARTIALLY_LOCKED has no meditation-level meaning (deriveMeditationBadge never returns
        // it -- it only exists for affirmation groups' "some collections locked" state) but the
        // `when` must stay exhaustive over the shared GroupBadge enum; falls back to the same
        // Premium styling as an inert default.
        GroupBadge.PREMIUM, GroupBadge.PARTIALLY_LOCKED -> Triple(
            if (dark) PremiumContainerDark else PremiumContainerLight,
            if (dark) OnPremiumContainerDark else OnPremiumContainerLight,
            stringResource(R.string.affirmation_group_badge_premium),
        )
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = contentColor,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(containerColor)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

/** Design 5a's "Unlock {name}" bottom sheet, opened by tapping any ad-unlockable
 *  [MeditationSessionCard] instead of the old inline "Watch ad" CTA. Body copy is chosen from the
 *  actual [AdUnlockPolicy] instead of the mockup's hardcoded "stays open for 24 hours" -- this
 *  app's meditation entries only ever use PER_USE (this playthrough only) or ONE_TIME_TRIAL (once,
 *  ever); TIMED_REPEATABLE is handled too, for parity with the shared access model, even though no
 *  catalog entry declares it today. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdUnlockSheet(
    entry: MeditationCatalogEntry,
    policy: AdUnlockPolicy,
    onWatchAd: () -> Unit,
    onUpgradeClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.meditation_ad_unlock_sheet_title, stringResource(entry.titleRes)),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = when (policy) {
                    AdUnlockPolicy.ONE_TIME_TRIAL -> stringResource(R.string.meditation_ad_unlock_body_one_time_trial)
                    AdUnlockPolicy.TIMED_REPEATABLE -> stringResource(
                        R.string.meditation_ad_unlock_body_timed,
                        entry.access.unlockWindowHours ?: 0,
                    )
                    AdUnlockPolicy.PER_USE, AdUnlockPolicy.NONE -> stringResource(R.string.meditation_ad_unlock_body_per_use)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(onClick = onWatchAd, modifier = Modifier.fillMaxWidth().padding(top = 22.dp)) {
                Text(stringResource(R.string.meditation_watch_ad_cta))
            }
            TextButton(onClick = onUpgradeClick, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(stringResource(R.string.affirmation_group_upgrade_cta))
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.meditation_ad_unlock_dismiss))
            }
        }
    }
}

private fun formatTime(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}
