package com.pirxhio.affirmity.ui.groups

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * The theme grain within a universe ("Your feed" refactor, scope decision #5): `id` is the same
 * dotted string already carried by [CatalogCollection.themeId] (format `"<universeId>.<slug>"`).
 * Hand-authored, unlike [CatalogCollection] -- `CatalogTaxonomy.kt` is generated and has no
 * first-class theme concept, only the id embedded in each collection.
 *
 * [label] is the English fallback, humanized from the slug half of [id]. User-visible text comes
 * from [titleRes] (see `CatalogThemeTitles.kt`) via [displayLabel]; never use [label] in the UI
 * or in analytics (use [id]).
 */
data class CatalogTheme(
    val id: String,
    val universeId: String,
    val label: String,
    @StringRes val titleRes: Int? = null,
)

/** Localized theme label for composition; falls back to the English [CatalogTheme.label]. */
@Composable
fun CatalogTheme.displayLabel(): String = titleRes?.let { stringResource(it) } ?: label

/** Localized theme label outside composition (e.g. sorting, search matching). */
fun CatalogTheme.displayLabel(resources: Resources): String = titleRes?.let(resources::getString) ?: label

/** Humanizes a snake_case slug into a sentence-cased label: `stop_procrastinating` ->
 *  "Stop procrastinating". Internal -- [catalogThemes] is the only intended caller for now. */
internal fun humanizeSlug(slug: String): String {
    val words = slug.split('_').filter { it.isNotBlank() }
    if (words.isEmpty()) return slug
    val first = words.first().replaceFirstChar { it.uppercase() }
    return if (words.size == 1) first else first + " " + words.drop(1).joinToString(" ")
}

/** Every distinct theme across all 226 catalog collections, derived -- never hand-maintained, so
 *  it stays in sync with `CatalogTaxonomy.kt` automatically (scope decision #1). */
fun catalogThemes(): List<CatalogTheme> = catalogThemesCache

private val catalogThemesCache: List<CatalogTheme> by lazy {
    catalogCollections()
        .distinctBy { it.themeId }
        .map { collection ->
            CatalogTheme(
                id = collection.themeId,
                universeId = collection.universeId,
                label = humanizeSlug(collection.themeId.substringAfterLast('.')),
                titleRes = catalogThemeTitleRes[collection.themeId],
            )
        }
}

/** Cached id-lookup, mirroring [catalogCollectionsById]. */
fun catalogThemesById(): Map<String, CatalogTheme> = catalogThemesByIdCache

private val catalogThemesByIdCache: Map<String, CatalogTheme> by lazy {
    catalogThemes().associateBy { it.id }
}
