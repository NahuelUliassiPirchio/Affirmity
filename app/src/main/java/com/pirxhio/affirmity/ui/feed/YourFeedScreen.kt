package com.pirxhio.affirmity.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.access.AccessDecision
import com.pirxhio.affirmity.data.local.FeedSources
import com.pirxhio.affirmity.ui.groups.CatalogTheme

/**
 * "Your feed" top composable (design §4, scope decision #3): current selection shown as
 * removable chips, a personalized 2-column grid of discovery surfaces, "See all themes", and the
 * sticky "Update my feed" CTA. Surface/theme detail navigation ([SurfaceDetailBottomSheet],
 * [SeeAllThemesScreen]) is owned by the caller (mirroring the old group-selector sheet's
 * MainActivity-level orchestration) -- this composable only ever reports intent via
 * [onOpenSurface]/[onSeeAllThemes].
 */
@Composable
fun YourFeedScreen(
    draftThemeIds: Set<String>,
    isDirty: Boolean,
    isValid: Boolean,
    catalogThemesById: Map<String, CatalogTheme>,
    recommendedSurfaces: List<SurfaceUiModel>,
    accessDecisionFor: (themeId: String) -> AccessDecision,
    onRemoveTheme: (themeId: String) -> Unit,
    onRemoveGroup: (universeId: String) -> Unit,
    onOpenSurface: (surfaceId: String) -> Unit,
    onSeeAllThemes: () -> Unit,
    onUpdateFeed: () -> Unit,
    onDone: () -> Unit,
    onFavoritesClick: () -> Unit,
    feedSources: FeedSources,
    onFeedSourcesChange: (FeedSources) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedThemes = draftThemeIds.mapNotNull { catalogThemesById[it] }.sortedBy { it.label }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.your_feed_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = onDone) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.your_feed_done),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f, fill = true)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            CurrentFeedSection(
                selectedThemes = selectedThemes,
                onRemoveTheme = onRemoveTheme,
                onRemoveGroup = onRemoveGroup,
                onSeeAllThemes = onSeeAllThemes,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            DiscoverySurfaceGrid(
                surfaces = recommendedSurfaces,
                draftThemeIds = draftThemeIds,
                onOpenSurface = onOpenSurface,
            )
            SeeAllThemesLink(
                onClick = onSeeAllThemes,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            )
            FeedSourcesSection(
                sources = feedSources,
                onChange = onFeedSourcesChange,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            )
            FavoritesEntryCard(
                onClick = onFavoritesClick,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
            )
        }

        UpdateFeedButton(isDirty = isDirty, isValid = isValid, onClick = onUpdateFeed)
    }
}

/** The two optional feed sources, on top of the theme selection: favourites and the user's own
 *  affirmations. Deliberately the same square card as [DiscoverySurfaceCard] above rather than a
 *  switch row, so the whole sheet reads as one grid of tappable tiles -- an "on" tile fills with the
 *  primary container colour instead of relying on a small switch thumb to carry the state. */
@Composable
private fun FeedSourcesSection(
    sources: FeedSources,
    onChange: (FeedSources) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.your_feed_sources_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FeedSourceCard(
                icon = Icons.Filled.Favorite,
                label = stringResource(R.string.your_feed_source_favorites),
                description = stringResource(R.string.your_feed_source_favorites_description),
                checked = sources.includeFavorites,
                onClick = { onChange(sources.copy(includeFavorites = !sources.includeFavorites)) },
                modifier = Modifier.weight(1f),
            )
            FeedSourceCard(
                icon = Icons.Filled.Edit,
                label = stringResource(R.string.your_feed_source_own),
                description = stringResource(R.string.your_feed_source_own_description),
                checked = sources.includeOwn,
                onClick = { onChange(sources.copy(includeOwn = !sources.includeOwn)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FeedSourceCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Every colour flips together so "on" is legible from across the room, not just at the corner
    // where a switch would sit.
    val container = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val onContainer = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    // No alpha on the "on" description: onPrimaryContainer only clears the 4.5:1 text minimum
    // against primaryContainer at full opacity (see ContrastRatioTest), and fading it was exactly
    // what made an enabled tile hard to read. The "off" tile can afford onSurfaceVariant because it
    // sits on the much lighter surfaceContainerLow.
    val secondary = if (checked) onContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(
            1.dp,
            if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Same circular icon badge DiscoverySurfaceCard uses, so an "off" tile is visually
            // indistinguishable from the grid above it and only "on" stands out.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (checked) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) onContainer else MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = onContainer,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = secondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** "See all themes" as its own bottom link (feedback: it belongs at the bottom of "Your feed",
 *  not only as the "+" quick-add chip in [CurrentFeedSection]) -- matches the exported design's
 *  centered, bordered "See all themes →" row below the discovery grid. */
@Composable
private fun SeeAllThemesLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.your_feed_see_all_themes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** Relocated from `SeeAllThemesScreen` (feedback: favorites belongs at the bottom of "Your feed",
 *  next to "See all themes", not buried inside the exhaustive theme browser). */
@Composable
private fun FavoritesEntryCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
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
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.affirmation_group_open_favorites),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * Docked bottom-sheet content for "Your feed" -- [BottomSheetScaffold]'s `sheetContent`.
 * Restores the old group-selector sheet's persistent peek/expand-collapse pattern instead of a
 * separate full-screen entry point: the peek row is always visible under the affirmations feed,
 * and tapping it toggles between collapsed and [YourFeedScreen] expanded, exactly like the old
 * sheet's Aplicar-on-peek-tap behavior.
 */
@Composable
fun YourFeedSheetContent(
    isExpanded: Boolean,
    draftThemeIds: Set<String>,
    isDirty: Boolean,
    isValid: Boolean,
    catalogThemesById: Map<String, CatalogTheme>,
    recommendedSurfaces: List<SurfaceUiModel>,
    accessDecisionFor: (themeId: String) -> AccessDecision,
    onRemoveTheme: (themeId: String) -> Unit,
    onRemoveGroup: (universeId: String) -> Unit,
    onOpenSurface: (surfaceId: String) -> Unit,
    onSeeAllThemes: () -> Unit,
    onUpdateFeed: () -> Unit,
    onDone: () -> Unit,
    onPeekClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    feedSources: FeedSources,
    onFeedSourcesChange: (FeedSources) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxHeight(0.85f)) {
        FeedSelectorPeekRow(
            isExpanded = isExpanded,
            // Tapping the peek strip while expanded commits the draft, same as "Update my feed"
            // -- mirrors the old selector sheet's GroupSelectorPeekRow, which called onApply here.
            onClick = if (isExpanded) onUpdateFeed else onPeekClick,
        )
        if (isExpanded) {
            YourFeedScreen(
                draftThemeIds = draftThemeIds,
                isDirty = isDirty,
                isValid = isValid,
                catalogThemesById = catalogThemesById,
                recommendedSurfaces = recommendedSurfaces,
                accessDecisionFor = accessDecisionFor,
                onRemoveTheme = onRemoveTheme,
                onRemoveGroup = onRemoveGroup,
                onOpenSurface = onOpenSurface,
                onSeeAllThemes = onSeeAllThemes,
                onUpdateFeed = onUpdateFeed,
                onDone = onDone,
                onFavoritesClick = onFavoritesClick,
                feedSources = feedSources,
                onFeedSourcesChange = onFeedSourcesChange,
                modifier = Modifier.weight(1f, fill = true),
            )
        }
    }
}

@Composable
private fun FeedSelectorPeekRow(isExpanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (isExpanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(
                    if (isExpanded) R.string.your_feed_collapse else R.string.your_feed_expand,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
