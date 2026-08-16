package com.pirxhio.affirmity.ui.meditation.catalog

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.pirxhio.affirmity.access.ContentAccess
import com.pirxhio.affirmity.meditation.MeditationDefinition

/**
 * One shipping guided meditation. Direct analogue of
 * [com.pirxhio.affirmity.ui.groups.AffirmationGroup]: UI metadata + a [ContentAccess] declaration
 * + a reference to the domain artifact, living in `ui/` so it may hold `@StringRes`/`ImageVector`,
 * while the domain artifact itself ([definition]) stays Android-free in `meditation/` and
 * plain-JUnit testable.
 *
 * [id] is the stable access identity: `ContentKey(ContentType.MEDITATION, id).storageKey` is what
 * Room and Firestore persist for a ONE_TIME_TRIAL grant, so it must never change once shipped.
 */
data class MeditationCatalogEntry(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    /** Second half of the Discover meta line ("10 min • Sueño") — the editorial category. */
    @StringRes val categoryRes: Int,
    val icon: ImageVector,
    /** Declared runtime in whole minutes. Pinned against the definition's real Fixed-duration sum
     * by a catalog-level test so the two can never silently drift. */
    val approxDurationMinutes: Int,
    /** The entire gating declaration. Consumed by `meditationAccessDecision` -> `resolveAccess`,
     * identically to how a group's is. */
    val access: ContentAccess,
    /** Lazy by decision: built on launch, never at class-init. Deferring construction avoids
     * turning a content authoring bug (a failed `require(...)` in a definition builder) into an
     * app-startup crash. */
    val definition: () -> MeditationDefinition,
    /** Everything the generic screen needs to render THIS entry. */
    val presentation: MeditationPresentation,
)
