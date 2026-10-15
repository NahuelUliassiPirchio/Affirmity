package com.pirxhio.affirmity.widget.carousel

import androidx.annotation.StringRes
import com.pirxhio.affirmity.access.AccessDecision
import com.pirxhio.affirmity.data.DefaultGroup
import com.pirxhio.affirmity.data.local.UserCollectionEntity
import com.pirxhio.affirmity.ui.groups.AffirmationGroup
import com.pirxhio.affirmity.ui.groups.GroupBadge
import com.pirxhio.affirmity.ui.groups.deriveCatalogBadge
import com.pirxhio.affirmity.ui.groups.selectableAffirmationGroups

/** Title of an option: a localized resource, or a user-provided name. */
sealed interface SourceTitle {
    data class Res(@param:StringRes val id: Int) : SourceTitle
    data class Text(val value: String) : SourceTitle
}

data class SourceOption(val source: CarouselSource, val title: SourceTitle, val badge: GroupBadge?)

/** The three kinds of source the configure screen offers. Every option is selectable. */
data class SourceOptions(
    val favorites: SourceOption,
    val themes: List<SourceOption>,
    val collections: List<SourceOption>,
)

/**
 * Pure model for the configure screen. Locked content is never hidden or disabled: picking it
 * simply renders the free fallback with a notice, so the badge is informational only.
 */
fun buildSourceOptions(
    partiallyLockedGroupIds: Set<String>,
    defaultGroups: List<DefaultGroup>,
    userCollections: List<UserCollectionEntity>,
    groups: List<AffirmationGroup> = selectableAffirmationGroups(),
): SourceOptions = SourceOptions(
    favorites = SourceOption(
        CarouselSource.Favorites,
        SourceTitle.Res(com.pirxhio.affirmity.R.string.your_feed_source_favorites),
        badge = null,
    ),
    themes = groups.map { group ->
        SourceOption(
            source = CarouselSource.Theme(group.id),
            title = SourceTitle.Res(group.titleRes),
            badge = deriveCatalogBadge(group, AccessDecision.Unlocked, group.id in partiallyLockedGroupIds),
        )
    },
    collections = defaultGroups.map {
        SourceOption(CarouselSource.Collection(it.id), SourceTitle.Res(it.nameRes), badge = null)
    } + userCollections.map {
        SourceOption(CarouselSource.Collection(it.id), SourceTitle.Text(it.name), badge = null)
    },
)
