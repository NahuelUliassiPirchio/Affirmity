package com.pirxhio.affirmity.widget.carousel

import androidx.compose.ui.graphics.Color
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.DefaultGroup
import com.pirxhio.affirmity.data.DefaultGroups
import com.pirxhio.affirmity.data.local.UserCollectionEntity
import com.pirxhio.affirmity.ui.feed.universeAccentOrNull
import com.pirxhio.affirmity.ui.groups.AffirmationGroup
import com.pirxhio.affirmity.ui.groups.selectableAffirmationGroups
import com.pirxhio.affirmity.ui.theme.GroupHighlight

/** The name shown in the header and the colour of the corner marker (design 8). */
data class CarouselTheme(val title: SourceTitle?, val highlight: Color) {
    companion object {
        /** Used for the free fallback and whenever the source can't be named. */
        val Default = CarouselTheme(title = null, highlight = GroupHighlight.Default.color)
    }
}

/**
 * Pure: names and colours a carousel from its [source]. Catalog themes reuse the feed's universe
 * accents, collections reuse their persisted [GroupHighlight]; favorites, the fallback pool and
 * anything unresolvable use the default highlight.
 */
fun carouselTheme(
    source: CarouselSource?,
    fallback: Boolean,
    groups: List<AffirmationGroup> = selectableAffirmationGroups(),
    defaultGroups: List<DefaultGroup> = DefaultGroups.all,
    userCollections: List<UserCollectionEntity> = emptyList(),
): CarouselTheme {
    if (fallback) return CarouselTheme.Default
    return when (source) {
        null -> CarouselTheme.Default
        CarouselSource.Favorites ->
            CarouselTheme(SourceTitle.Res(R.string.your_feed_source_favorites), GroupHighlight.Default.color)
        is CarouselSource.Theme -> groups.firstOrNull { it.id == source.groupId }?.let {
            CarouselTheme(SourceTitle.Res(it.titleRes), universeAccentOrNull(it.id) ?: GroupHighlight.Default.color)
        } ?: CarouselTheme.Default
        is CarouselSource.Collection ->
            defaultGroups.firstOrNull { it.id == source.collectionId }?.let {
                CarouselTheme(SourceTitle.Res(it.nameRes), GroupHighlight.fromId(it.highlightId).color)
            } ?: userCollections.firstOrNull { it.id == source.collectionId }?.let {
                CarouselTheme(SourceTitle.Text(it.name), GroupHighlight.fromId(it.highlightId).color)
            } ?: CarouselTheme.Default
    }
}
