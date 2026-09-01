package com.pirxhio.affirmity.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.access.AccessDecision
import com.pirxhio.affirmity.analytics.AnalyticsContentType
import com.pirxhio.affirmity.analytics.AnalyticsEvent
import com.pirxhio.affirmity.analytics.AnalyticsId
import com.pirxhio.affirmity.analytics.provenance
import com.pirxhio.affirmity.ui.groups.CatalogTheme
import com.pirxhio.affirmity.ui.groups.catalogThemes
import com.pirxhio.affirmity.ui.groups.isThemeToggleable

private enum class ThemeFilter { ALL, SELECTED, FREE, PREMIUM }

/**
 * Search + Selected/Free/Premium filters + alphabetical theme list (design §4), adapted from the
 * old selector sheet's `LazyColumn` shell but iterating [catalogThemes] instead of
 * `catalogUniverseGroups()`, wired to the same [draftThemeIds]/`onToggleTheme`. Also hosts the
 * moved-in add-custom entry point (scope decision #2) since `personalizadas` no longer has a slot
 * in the toggleable theme grid -- favorites now lives at the bottom of `YourFeedScreen` instead.
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

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.your_feed_search_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
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
        items(visibleThemes, key = { it.id }) { theme: CatalogTheme ->
            val decision = accessDecisionFor(theme.id)
            if (isThemeToggleable(decision)) {
                ThemeSelectionRow(
                    label = theme.label,
                    checked = theme.id in draftThemeIds,
                    onToggle = { onToggleTheme(theme.id) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            } else {
                PremiumThemeRow(
                    label = theme.label,
                    onUpgradeClick = {
                        onEvent(
                            AnalyticsEvent.ContentLockedTapped(
                                AnalyticsId.of(theme),
                                AnalyticsContentType.AFFIRMATION_GROUP,
                                decision.provenance(),
                            ),
                        )
                        onUpgradeClick()
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        item {
            AddCustomAffirmationsCard(
                onClick = onAddCustomClick,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
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
