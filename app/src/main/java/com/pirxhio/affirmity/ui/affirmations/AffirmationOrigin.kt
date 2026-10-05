package com.pirxhio.affirmity.ui.affirmations

import androidx.annotation.StringRes
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.Affirmation
import com.pirxhio.affirmity.data.AffirmationSource
import com.pirxhio.affirmity.ui.groups.catalogCollectionsById
import com.pirxhio.affirmity.ui.groups.catalogThemesById
import com.pirxhio.affirmity.ui.groups.catalogUniverseGroups

/** Where an affirmation lives in the catalog: its universe and, for catalog rows, its theme. */
internal data class AffirmationOrigin(
    @StringRes val universeTitleRes: Int,
    @StringRes val themeTitleRes: Int?,
    val themeFallbackLabel: String? = null,
)

/** Null when the universe is unknown, so the caller can omit the header instead of showing a blank one. */
internal fun affirmationOrigin(affirmation: Affirmation): AffirmationOrigin? {
    if (affirmation.source == AffirmationSource.OWNED) {
        return AffirmationOrigin(R.string.affirmation_group_personalizadas_title, themeTitleRes = null)
    }
    val universe = catalogUniverseGroups().firstOrNull { it.id == affirmation.groupId } ?: return null
    val themeId = affirmation.collectionId?.let { catalogCollectionsById()[it]?.themeId }
    val theme = themeId?.let { catalogThemesById()[it] }
    return AffirmationOrigin(universe.titleRes, theme?.titleRes, theme?.label)
}
