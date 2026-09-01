package com.pirxhio.affirmity.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 2-column discovery grid (design §4). Deliberately a plain `Column`/`Row` layout, not a
 * `LazyVerticalGrid` -- [surfaces] is bounded (one card per universe, currently 14), and this
 * screen is already one scrollable column ([YourFeedScreen]), so a nested lazy grid would need an
 * explicit height to avoid the "unbounded height" crash. `draftThemeIds` decides each card's
 * "N of M selected" -- always derived, never stored.
 */
@Composable
fun DiscoverySurfaceGrid(
    surfaces: List<SurfaceUiModel>,
    draftThemeIds: Set<String>,
    onOpenSurface: (surfaceId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        surfaces.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { surface ->
                    val selectedCount = surface.themeIds.count { it in draftThemeIds }
                    DiscoverySurfaceCard(
                        surface = surface,
                        selectedCount = selectedCount,
                        onClick = { onOpenSurface(surface.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
