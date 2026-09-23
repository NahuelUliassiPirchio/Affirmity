package com.pirxhio.affirmity.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.access.AccessDecision
import com.pirxhio.affirmity.analytics.AnalyticsEvent
import com.pirxhio.affirmity.ui.groups.AffirmationGroup
import com.pirxhio.affirmity.ui.groups.CatalogTheme
import com.pirxhio.affirmity.ui.groups.catalogThemes
import com.pirxhio.affirmity.ui.groups.catalogUniverseGroups
import com.pirxhio.affirmity.ui.groups.isThemeToggleable

private enum class ThemeFilter { ALL, SELECTED, FREE, PREMIUM }

/** Themes shown collapsed before a group offers a "+N more" expander -- browse groups run much
 *  larger than "Feeding you now"'s selected-only groups, so this stays looser than that section's
 *  [CurrentFeedSection]-local collapse count while keeping the same collapse/expand mechanic. */
private const val COLLAPSED_THEME_CHIP_COUNT = 6

/**
 * Search + Selected/Free/Premium filters + theme browser (design §4), redesigned to match
 * "Feeding you now" ([CurrentFeedSection]): a blank query groups [visibleThemes] by universe into
 * a two-column grid of accent-colored [ThemeGroupTile]s that open in place (full-width) to reveal
 * their themes as wrapped chips, instead of the old flat alphabetical
 * `LazyColumn` of full-width rows -- ~200 themes as full-width rows made the list a long,
 * undifferentiated scroll with no sense of the underlying taxonomy. A non-blank query keeps a
 * flat, ungrouped result list (grouping a small cross-cutting match set doesn't help) but renders
 * matches with the same chip visual language for consistency between the two modes.
 *
 * Also hosts the moved-in add-custom entry point (scope decision #2) since `personalizadas` no
 * longer has a slot in the toggleable theme grid -- favorites now lives at the bottom of
 * `YourFeedScreen` instead.
 *
 * There is no "New" filter: the catalog carries no per-theme novelty signal (no addition date) to
 * derive one from, so this implements Selected/Free/Premium only -- see the migration report.
 */
@Composable
fun SeeAllThemesScreen(
    draftThemeIds: Set<String>,
    accessDecisionFor: (themeId: String) -> AccessDecision,
    onToggleTheme: (themeId: String) -> Unit,
    onUpgradeClick: () -> Unit,
    onEvent: (AnalyticsEvent) -> Unit,
    onAddCustomClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ThemeFilter.ALL) }

    val allThemes = remember { catalogThemes().sortedBy { it.label } }
    // Keyed on query/filter/draftThemeIds so a keystroke in the search field doesn't re-run a
    // full catalog filter (incl. an accessDecisionFor call per theme for FREE/PREMIUM) on every
    // recomposition.
    val visibleThemes = remember(query, filter, draftThemeIds) {
        allThemes.filter { theme ->
            val matchesQuery = query.isBlank() || theme.label.contains(query, ignoreCase = true)
            val matchesFilter = when (filter) {
                ThemeFilter.ALL -> true
                ThemeFilter.SELECTED -> theme.id in draftThemeIds
                ThemeFilter.FREE -> isThemeToggleable(accessDecisionFor(theme.id))
                ThemeFilter.PREMIUM -> !isThemeToggleable(accessDecisionFor(theme.id))
            }
            matchesQuery && matchesFilter
        }
    }

    // Grouped only while browsing (blank query) -- a search result set is small and cross-cutting,
    // so grouping it by universe would fragment it rather than help scanning.
    val groupedThemes = remember(visibleThemes, query) {
        if (query.isBlank()) {
            catalogUniverseGroups().mapNotNull { group ->
                visibleThemes.filter { it.universeId == group.id }
                    .takeIf { it.isNotEmpty() }
                    ?.let { group to it }
            }
        } else {
            emptyList()
        }
    }

    // Local UI state only -- never committed to app state, reset is fine on process death.
    // Two independent levels: a tile being open (full-width, chips visible) and, within an open
    // tile, its chip list being past the COLLAPSED_THEME_CHIP_COUNT cut.
    val expandedGroupIds = remember { mutableStateMapOf<String, Boolean>() }
    val showAllThemesGroupIds = remember { mutableStateMapOf<String, Boolean>() }
    // Snapshotted here, in composition, rather than read from inside the `span` lambda: the grid
    // caches its line/span layout per item-provider, so a span that silently depended on state
    // read only at measure time could go stale. Reading it here recomposes the grid content (new
    // item provider) whenever a tile opens or closes, which re-derives every span.
    val expandedIds = expandedGroupIds.filterValues { it }.keys

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        item(span = FullLineSpan) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.your_feed_see_all_themes),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(R.string.your_feed_done))
                }
            }
        }
        item(span = FullLineSpan) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.your_feed_search_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )
        }
        item(span = FullLineSpan) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            ) {
                FilterChip(
                    selected = filter == ThemeFilter.SELECTED,
                    onClick = { filter = if (filter == ThemeFilter.SELECTED) ThemeFilter.ALL else ThemeFilter.SELECTED },
                    label = { Text(stringResource(R.string.your_feed_filter_selected)) },
                )
                FilterChip(
                    selected = filter == ThemeFilter.FREE,
                    onClick = { filter = if (filter == ThemeFilter.FREE) ThemeFilter.ALL else ThemeFilter.FREE },
                    label = { Text(stringResource(R.string.your_feed_filter_free)) },
                )
                FilterChip(
                    selected = filter == ThemeFilter.PREMIUM,
                    onClick = { filter = if (filter == ThemeFilter.PREMIUM) ThemeFilter.ALL else ThemeFilter.PREMIUM },
                    label = { Text(stringResource(R.string.your_feed_filter_premium)) },
                )
            }
        }
        if (query.isBlank()) {
            items(
                items = groupedThemes,
                key = { (group, _) -> group.id },
                // An open tile claims the whole line so its chips get full width; closed tiles
                // share a line two-up. The grid reflows the following tiles onto new lines.
                span = { (group, _) -> GridItemSpan(if (group.id in expandedIds) maxLineSpan else 1) },
            ) { (group, themes) ->
                ThemeGroupTile(
                    group = group,
                    themes = themes,
                    draftThemeIds = draftThemeIds,
                    accessDecisionFor = accessDecisionFor,
                    isExpanded = group.id in expandedIds,
                    onToggleExpanded = { expandedGroupIds[group.id] = group.id !in expandedIds },
                    showAllThemes = showAllThemesGroupIds[group.id] == true,
                    onToggleShowAllThemes = {
                        showAllThemesGroupIds[group.id] = !(showAllThemesGroupIds[group.id] == true)
                    },
                    onToggleTheme = onToggleTheme,
                    onUpgradeClick = onUpgradeClick,
                    onEvent = onEvent,
                    modifier = Modifier
                        .animateItem()
                        .padding(vertical = 6.dp),
                )
            }
        } else {
            item(span = FullLineSpan) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    visibleThemes.forEach { theme ->
                        CatalogThemeChip(
                            theme = theme,
                            checked = theme.id in draftThemeIds,
                            decision = accessDecisionFor(theme.id),
                            onToggleTheme = onToggleTheme,
                            onUpgradeClick = onUpgradeClick,
                            onEvent = onEvent,
                        )
                    }
                }
            }
        }
        item(span = FullLineSpan) {
            AddCustomAffirmationsCard(
                onClick = onAddCustomClick,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
    }
}

/** Header, search, filters and the trailing add-custom card always take the whole grid line. */
private val FullLineSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

private val TileShape = RoundedCornerShape(20.dp)

/**
 * One universe as a browse-grid tile, carrying its own accent ([universeAccentColor]) as a faint
 * container wash plus a [HandDrawnHighlight] marker smear behind the icon -- the flat, single-tone
 * stacked cards this replaces read as "a list without soul", with 14 near-identical rows and no
 * way to tell universes apart at a glance.
 *
 * Collapsed ([isExpanded] false): a half-width tile (icon, title, "N of M selected"), sized like
 * [DiscoverySurfaceCard] but with a fixed 3-line title slot so two-up neighbours stay the same
 * height. Expanded: the grid gives it the full line (see the `span` in [SeeAllThemesScreen]) and
 * it becomes a horizontal header above its themes as wrapped [CatalogThemeChip]s, collapsed to
 * [COLLAPSED_THEME_CHIP_COUNT] plus a "+N more" expander until [showAllThemes] -- the same
 * mechanic the previous `ThemeGroupCard` used.
 */
@Composable
private fun ThemeGroupTile(
    group: AffirmationGroup,
    themes: List<CatalogTheme>,
    draftThemeIds: Set<String>,
    accessDecisionFor: (themeId: String) -> AccessDecision,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    showAllThemes: Boolean,
    onToggleShowAllThemes: () -> Unit,
    onToggleTheme: (themeId: String) -> Unit,
    onUpgradeClick: () -> Unit,
    onEvent: (AnalyticsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(group.titleRes)
    val accent = universeAccentColor(group.id)
    val ink = universeAccentInk(accent)
    val selectedCount = themes.count { it.id in draftThemeIds }
    val countText = stringResource(R.string.your_feed_selected_count, selectedCount, themes.size)
    val toggleLabel = if (isExpanded) {
        stringResource(R.string.your_feed_collapse_group_a11y, title)
    } else {
        stringResource(R.string.your_feed_expand_group_a11y, title)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = TileShape,
        colors = CardDefaults.cardColors(containerColor = universeAccentContainer(accent)),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
    ) {
        if (!isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TileShape)
                    .clickable(onClickLabel = toggleLabel, role = Role.Button, onClick = onToggleExpanded)
                    .padding(16.dp),
            ) {
                AccentIcon(group = group, accent = accent, ink = ink)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    minLines = 3,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    text = countText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            val visibleThemes = if (showAllThemes) themes else themes.take(COLLAPSED_THEME_CHIP_COUNT)
            val hiddenCount = themes.size - visibleThemes.size

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TileShape)
                        .clickable(onClickLabel = toggleLabel, role = Role.Button, onClick = onToggleExpanded)
                        .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
                ) {
                    AccentIcon(group = group, accent = accent, ink = ink)
                    Column(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .weight(1f),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = countText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ExpandLess,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                ) {
                    visibleThemes.forEach { theme ->
                        CatalogThemeChip(
                            theme = theme,
                            checked = theme.id in draftThemeIds,
                            decision = accessDecisionFor(theme.id),
                            onToggleTheme = onToggleTheme,
                            onUpgradeClick = onUpgradeClick,
                            onEvent = onEvent,
                        )
                    }
                    if (hiddenCount > 0) {
                        AssistChip(
                            onClick = onToggleShowAllThemes,
                            label = { Text(stringResource(R.string.your_feed_more_chip, hiddenCount)) },
                        )
                    }
                }
            }
        }
    }
}

/** The group's icon over its seeded marker smear, inked from the accent so hue and glyph read
 *  as one mark. Wider than tall so the smear reads as a highlighter swipe, not a badge. */
@Composable
private fun AccentIcon(group: AffirmationGroup, accent: Color, ink: Color) {
    Box(
        modifier = Modifier.size(width = 60.dp, height = 44.dp),
        contentAlignment = Alignment.Center,
    ) {
        HandDrawnHighlight(
            seedKey = group.id,
            color = accent,
            modifier = Modifier.matchParentSize(),
        )
        Icon(
            imageVector = group.icon,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(26.dp),
        )
    }
}

/** Relocated from the now-deleted `AffirmationGroupSelectorSheet.kt` (scope decision #2):
 *  `personalizadas` no longer has a toggle slot, but its entry points must stay reachable. */
@Composable
private fun AddCustomAffirmationsCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.affirmation_group_add_custom),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
