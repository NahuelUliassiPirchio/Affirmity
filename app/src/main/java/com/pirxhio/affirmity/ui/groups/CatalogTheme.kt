package com.pirxhio.affirmity.ui.groups

/**
 * The theme grain within a universe ("Your feed" refactor, scope decision #5): `id` is the same
 * dotted string already carried by [CatalogCollection.themeId] (format `"<universeId>.<slug>"`).
 * Hand-authored, unlike [CatalogCollection] -- `CatalogTaxonomy.kt` is generated and has no
 * first-class theme concept, only the id embedded in each collection.
 *
 * [label] is a PLACEHOLDER (scope decision #1): humanized from the slug half of [id]
 * (`stop_procrastinating` -> "Stop procrastinating"), not a real localized string -- there is no
 * per-theme string resource in `CatalogTaxonomy.kt` ("DO NOT EDIT BY HAND") to draw from, and
 * hand-authoring ~60-90 new localized strings for every theme slug is out of scope for this pass.
 * Swappable for real i18n later without touching any call site, since callers only ever read
 * [CatalogTheme.label].
 */
data class CatalogTheme(
    val id: String,
    val universeId: String,
    val label: String,
)

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
            )
        }
}

/** Cached id-lookup, mirroring [catalogCollectionsById]. */
fun catalogThemesById(): Map<String, CatalogTheme> = catalogThemesByIdCache

private val catalogThemesByIdCache: Map<String, CatalogTheme> by lazy {
    catalogThemes().associateBy { it.id }
}
