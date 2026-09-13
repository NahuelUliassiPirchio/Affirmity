package com.pirxhio.affirmity.ui.meditation.catalog

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.pirxhio.affirmity.R
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
     *  - the one-line [MeditationPrimer.shortRes] rendered on that shelf's cards,
     *  - tapping the card opening [MeditationPrimer]'s explanation sheet instead of launching.
     *
     * Left null for the ~34 entries whose title already explains itself (including "Bondad
     * amorosa"), because every extra line on a shelf card is paid for by every card in that shelf. */
    val primer: MeditationPrimer? = null,
)

/** [R.string.guided_meditation_idle_duration_minutes] normally, or the "~"-prefixed
 *  [R.string.guided_meditation_idle_duration_minutes_adjustable] variant when this entry has
 *  pre-session customization fields -- [approxDurationMinutes] is only accurate for this entry's
 *  *default* configuration (pinned by a catalog test), and every shipped [CustomizationField] lets
 *  the user pick a session length materially different from that default (round counts, per-phase
 *  seconds, or a duration-in-minutes picker). Showing the plain, unqualified string on these cards
 *  reads as a promise the app doesn't keep once the user customizes the session. */
@get:StringRes
val MeditationCatalogEntry.durationLabelRes: Int
    get() = if (customizationFields.isEmpty()) {
        R.string.guided_meditation_idle_duration_minutes
    } else {
        R.string.guided_meditation_idle_duration_minutes_adjustable
    }

/**
 * Plain-words explanation of a meditation whose name is opaque to a newcomer, shown on the
 * Discover surface. Copy is authored to be read at a glance: no Sanskrit gloss, no "ancient
 * practice" framing.
 */
data class MeditationPrimer(
    /** One line, rendered under the title on the "Vale la pena conocerlas" shelf only. */
    @StringRes val shortRes: Int,
    /** The full explanation, rendered as the body of the primer sheet. */
    @StringRes val longRes: Int,
    /** "Qué esperar" bullets -- concrete, practical expectations (posture, eyes, what counts as
     *  doing it right), not benefits. */
    val expectationsRes: List<Int>,
    /** A plain-language rendering of an impenetrable title ("Mirada en un punto" for Trataka),
     *  shown under it in the sheet. Null when the title is already plain. */
    @StringRes val plainNameRes: Int? = null,
)
