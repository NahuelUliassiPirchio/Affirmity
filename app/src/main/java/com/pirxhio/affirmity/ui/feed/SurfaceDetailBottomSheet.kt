package com.pirxhio.affirmity.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.access.AccessDecision
import com.pirxhio.affirmity.analytics.AnalyticsContentType
import com.pirxhio.affirmity.analytics.AnalyticsEvent
import com.pirxhio.affirmity.analytics.AnalyticsId
import com.pirxhio.affirmity.analytics.provenance
import com.pirxhio.affirmity.ui.groups.catalogThemesById
import com.pirxhio.affirmity.ui.groups.isThemeToggleable

/**
 * Detail sheet opened from a [DiscoverySurfaceCard] tap (design §4): "Suggested for you"
 * ([SurfaceUiModel.recommendedThemeIds], checkmark reflects [draftThemeIds] membership -- never
 * auto-checked), "More in {surface}" (the remaining toggleable themes), and "Go deeper" (themes
 * where [accessDecisionFor] resolves locked -- PRO badge, routes to upgrade).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurfaceDetailBottomSheet(
    surface: SurfaceUiModel,
    draftThemeIds: Set<String>,
    accessDecisionFor: (themeId: String) -> AccessDecision,
    onToggleTheme: (themeId: String) -> Unit,
    onUpgradeClick: () -> Unit,
    onEvent: (AnalyticsEvent) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
) {
    val themesById = catalogThemesById()
    val recommendedIds = surface.recommendedThemeIds.toSet()
    val toggleableIds = surface.themeIds.filter { isThemeToggleable(accessDecisionFor(it)) }
    val lockedIds = surface.themeIds - toggleableIds.toSet()
    val suggested = surface.themeIds.filter { it in recommendedIds && it in toggleableIds }
    val more = toggleableIds.filterNot { it in recommendedIds }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = stringResource(surface.titleRes),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (suggested.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(R.string.your_feed_suggested_for_you))
                }
                items(suggested, key = { "suggested-$it" }) { themeId ->
                    ThemeRowItem(themeId, themesById, draftThemeIds, recommended = true, onToggleTheme)
                }
            }
            if (more.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(R.string.your_feed_more_in, stringResource(surface.titleRes)))
                }
                items(more, key = { "more-$it" }) { themeId ->
                    ThemeRowItem(themeId, themesById, draftThemeIds, recommended = false, onToggleTheme)
                }
            }
            if (lockedIds.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(R.string.your_feed_go_deeper))
                }
                items(lockedIds, key = { "locked-$it" }) { themeId ->
                    PremiumThemeRow(
                        label = themesById[themeId]?.label.orEmpty(),
                        onUpgradeClick = {
                            themesById[themeId]?.let { theme ->
                                onEvent(
                                    AnalyticsEvent.ContentLockedTapped(
                                        AnalyticsId.of(theme),
                                        AnalyticsContentType.AFFIRMATION_GROUP,
                                        accessDecisionFor(themeId).provenance(),
                                    ),
                                )
                            }
                            onUpgradeClick()
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            item { Column(modifier = Modifier.padding(bottom = 24.dp)) {} }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun ThemeRowItem(
    themeId: String,
    themesById: Map<String, com.pirxhio.affirmity.ui.groups.CatalogTheme>,
    draftThemeIds: Set<String>,
    recommended: Boolean,
    onToggleTheme: (String) -> Unit,
) {
    ThemeSelectionRow(
        label = themesById[themeId]?.label.orEmpty(),
        checked = themeId in draftThemeIds,
        recommended = recommended,
        onToggle = { onToggleTheme(themeId) },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
