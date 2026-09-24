package com.pirxhio.affirmity.ui.meditation.catalog

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.pirxhio.affirmity.access.ContentAccess
import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.customization.CustomizationField

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
     * app-startup crash.
     *
     * Takes the confirmed customization values (keyed by each [CustomizationField]'s `key`, string
     * -encoded per that type's docs) collected by the pre-session customization screen. Entries
     * with an empty [customizationFields] ignore the map — it is `emptyMap()` in that case, so
     * behavior for every entry without fields is identical to the old no-arg factory. */
    val definition: (config: Map<String, String>) -> MeditationDefinition,
    /** Everything the generic screen needs to render THIS entry. */
    val presentation: MeditationPresentation,
    /** Adjustable knobs shown on the pre-session customization screen before [definition] is
     * built. Empty (the default) means the entry launches straight into the session, exactly as
     * before this field existed. */
    val customizationFields: List<CustomizationField> = emptyList(),
    /** Set only on entries whose [titleRes] tells a newcomer nothing about what they will actually
     * do ("Trataka", "Yoga Nidra", "Vipassana"). Its presence is the ONLY signal driving all three
     * of the Discover behaviours below -- there is deliberately no second boolean flag:
     *  - membership in the "Vale la pena conocerlas" shelf,
     *  - the card's headline swapping to [MeditationPrimer.cardHeadlineRes] (design 6a) in every
     *    shelf the entry appears in, not just that one,
     *  - tapping the card opening [MeditationPrimer]'s explanation sheet instead of launching.
     *
     * Left null for entries whose title already explains itself (including "Bondad amorosa"). */
    val primer: MeditationPrimer? = null,
)

/**
 * Plain-words explanation of a meditation whose name is opaque to a newcomer, shown on the
 * Discover surface. Copy is authored to be read at a glance: no Sanskrit gloss, no "ancient
 * practice" framing.
 */
data class MeditationPrimer(
    /** The full explanation, rendered as the body of the primer sheet. */
    @StringRes val longRes: Int,
    /** "Qué esperar" bullets -- concrete, practical expectations (posture, eyes, what counts as
     *  doing it right), not benefits. */
    val expectationsRes: List<Int>,
    /** A plain-language rendering of an impenetrable title ("Mirada en un punto" for Trataka),
     *  shown under it in the sheet. Null when the title is already plain. */
    @StringRes val plainNameRes: Int? = null,
    /** A 1-3 word plain-language label used as the shelf/grid card's headline in place of the real
     *  title (design 6a: "Candle gazing" instead of "Trataka"), with the real title moving into the
     *  card's meta line next to duration. A separate field from [plainNameRes] because a card
     *  headline needs to be genuinely short to avoid wrapping badly at the shelf card's fixed
     *  width -- when [plainNameRes] already fits that budget (e.g. Bhramari's two-word phrase),
     *  the two may hold the same string; when it doesn't, this one carries a shorter substitute.
     *  Null falls back to the real title, identical to how an entry with no [plainNameRes] behaves
     *  in the detail sheet. */
    @StringRes val cardHeadlineRes: Int? = null,
)
