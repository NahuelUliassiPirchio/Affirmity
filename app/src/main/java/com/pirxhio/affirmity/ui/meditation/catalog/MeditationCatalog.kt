package com.pirxhio.affirmity.ui.meditation.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Spa
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.access.ContentAccess
import com.pirxhio.affirmity.meditation.bodyscan.BodyScanText
import com.pirxhio.affirmity.meditation.bodyscan.bodyScanMeditationDefinition
import com.pirxhio.affirmity.meditation.breathing.BreathingText
import com.pirxhio.affirmity.meditation.calm.CalmText
import com.pirxhio.affirmity.meditation.calm.calmMeditationDefinition
import com.pirxhio.affirmity.meditation.focus.FocusText
import com.pirxhio.affirmity.meditation.focus.focusMeditationDefinition
import com.pirxhio.affirmity.meditation.quickreset.QuickResetText
import com.pirxhio.affirmity.meditation.quickreset.quickResetMeditationDefinition
import com.pirxhio.affirmity.meditation.selfcompassion.SelfCompassionLength
import com.pirxhio.affirmity.meditation.selfcompassion.SelfCompassionText
import com.pirxhio.affirmity.meditation.selfcompassion.selfCompassionMeditationDefinition
import com.pirxhio.affirmity.meditation.sleep.SleepText
import com.pirxhio.affirmity.meditation.sleep.sleepMeditationDefinition

/**
 * The meditation registry (REQ-4.6). No DI, no persistence, no remote fetch — mirrors
 * `com.pirxhio.affirmity.ui.groups.selectableAffirmationGroups`. Entry order and access companions
 * are exactly the REQ-4.6 table; `MeditationCatalogTest` assertion #1 pins both.
 *
 * Every entry's `presentation.audioResources` is `emptyMap()` (design §7.3, acceptance criterion
 * 3): all 7 entries ship silent, `PlayVoice`/`StartAmbient` ids stay symbolic until a real asset
 * is authored. `presentation.textResources` maps every `ShowText` id reachable from the entry's
 * own definition tree — including the shared [BreathingText.INHALE]/[BreathingText.EXHALE] ids
 * every entry emits via `breathingBlock()` — to the same strings the breathing demo already uses.
 */
fun meditationCatalog(): List<MeditationCatalogEntry> = listOf(
    MeditationCatalogEntry(
        id = "reset_rapido",
        titleRes = R.string.meditation_catalog_reset_rapido_title,
        descriptionRes = R.string.meditation_catalog_reset_rapido_description,
        categoryRes = R.string.meditation_catalog_category_relajacion,
        icon = Icons.Filled.Bolt,
        approxDurationMinutes = 2,
        access = ContentAccess.Free,
        definition = { quickResetMeditationDefinition() },
        presentation = MeditationPresentation(
            textResources = mapOf(
                QuickResetText.INTRO to R.string.meditation_quickreset_intro,
                BreathingText.INHALE to R.string.guided_meditation_inhale,
                BreathingText.EXHALE to R.string.guided_meditation_exhale,
                QuickResetText.CLOSING to R.string.meditation_quickreset_closing,
            ),
            audioResources = emptyMap(),
            counters = listOf(
                MeditationCounter(
                    repeatId = "qr_breathing",
                    total = 8,
                    labelRes = R.string.guided_meditation_breath_label,
                    emphasis = CounterEmphasis.SECONDARY,
                ),
            ),
        ),
    ),
    MeditationCatalogEntry(
        id = "calma",
        titleRes = R.string.meditation_catalog_calma_title,
        descriptionRes = R.string.meditation_catalog_calma_description,
        categoryRes = R.string.meditation_catalog_category_calma,
        icon = Icons.Filled.Spa,
        approxDurationMinutes = 5,
        access = ContentAccess.Free,
        definition = { calmMeditationDefinition() },
        presentation = MeditationPresentation(
            textResources = mapOf(
                CalmText.INTRO to R.string.meditation_calm_intro,
                BreathingText.INHALE to R.string.guided_meditation_inhale,
                CalmText.HOLD to R.string.meditation_calm_hold,
                BreathingText.EXHALE to R.string.guided_meditation_exhale,
                CalmText.REST to R.string.meditation_calm_rest,
                CalmText.CLOSING to R.string.meditation_calm_closing,
            ),
            audioResources = emptyMap(),
            counters = listOf(
                MeditationCounter(
                    repeatId = "calm_breathing",
                    total = 20,
                    labelRes = R.string.guided_meditation_breath_label,
                    emphasis = CounterEmphasis.SECONDARY,
                ),
            ),
        ),
    ),
    MeditationCatalogEntry(
        id = "enfoque",
        titleRes = R.string.meditation_catalog_enfoque_title,
        descriptionRes = R.string.meditation_catalog_enfoque_description,
        categoryRes = R.string.meditation_catalog_category_enfoque,
        icon = Icons.Filled.CenterFocusStrong,
        approxDurationMinutes = 10,
        access = ContentAccess.ProOrAdPerUse,
        definition = { focusMeditationDefinition() },
        presentation = MeditationPresentation(
            textResources = mapOf(
                FocusText.INTRO to R.string.meditation_focus_intro,
                BreathingText.INHALE to R.string.guided_meditation_inhale,
                FocusText.HOLD to R.string.meditation_focus_hold,
                BreathingText.EXHALE to R.string.guided_meditation_exhale,
                FocusText.ANCHOR to R.string.meditation_focus_anchor,
                FocusText.CLOSING to R.string.meditation_focus_closing,
            ),
            audioResources = emptyMap(),
            counters = listOf(
                MeditationCounter(
                    repeatId = "focus_breathing",
                    total = 30,
                    labelRes = R.string.guided_meditation_breath_label,
                    emphasis = CounterEmphasis.SECONDARY,
                ),
            ),
        ),
    ),
    MeditationCatalogEntry(
        id = "dormir",
        titleRes = R.string.meditation_catalog_dormir_title,
        descriptionRes = R.string.meditation_catalog_dormir_description,
        categoryRes = R.string.meditation_catalog_category_sueno,
        icon = Icons.Filled.Bedtime,
        approxDurationMinutes = 10,
        access = ContentAccess.ProOrAdTrial,
        definition = { sleepMeditationDefinition() },
        presentation = MeditationPresentation(
            textResources = mapOf(
                SleepText.SETTLE to R.string.meditation_sleep_settle,
                SleepText.GUIDANCE to R.string.meditation_sleep_guidance,
                BreathingText.INHALE to R.string.guided_meditation_inhale,
                BreathingText.EXHALE to R.string.guided_meditation_exhale,
                SleepText.REST_1 to R.string.meditation_sleep_rest_1,
                SleepText.RELEASE to R.string.meditation_sleep_release,
                SleepText.REST_2 to R.string.meditation_sleep_rest_2,
                SleepText.CLOSING to R.string.meditation_sleep_closing,
            ),
            audioResources = emptyMap(),
            counters = listOf(
                MeditationCounter(
                    repeatId = "sleep_breathing",
                    total = 12,
                    labelRes = R.string.guided_meditation_breath_label,
                    emphasis = CounterEmphasis.SECONDARY,
                ),
            ),
        ),
    ),
    MeditationCatalogEntry(
        id = "body_scan",
        titleRes = R.string.meditation_catalog_body_scan_title,
        descriptionRes = R.string.meditation_catalog_body_scan_description,
        categoryRes = R.string.meditation_catalog_category_cuerpo,
        icon = Icons.Filled.Accessibility,
        approxDurationMinutes = 10,
        access = ContentAccess.Pro,
        definition = { bodyScanMeditationDefinition() },
        presentation = MeditationPresentation(
            textResources = mapOf(
                BodyScanText.INTRO to R.string.meditation_bodyscan_intro,
                BreathingText.INHALE to R.string.guided_meditation_inhale,
                BreathingText.EXHALE to R.string.guided_meditation_exhale,
                BodyScanText.FEET to R.string.meditation_bodyscan_feet,
                BodyScanText.LEGS to R.string.meditation_bodyscan_legs,
                BodyScanText.TORSO to R.string.meditation_bodyscan_torso,
                BodyScanText.ARMS to R.string.meditation_bodyscan_arms,
                BodyScanText.HEAD to R.string.meditation_bodyscan_head,
                BodyScanText.WHOLE to R.string.meditation_bodyscan_whole,
                BodyScanText.CLOSING to R.string.meditation_bodyscan_closing,
            ),
            audioResources = emptyMap(),
            counters = listOf(
                MeditationCounter(
                    repeatId = "bs_breathing",
                    total = 6,
                    labelRes = R.string.guided_meditation_breath_label,
                    emphasis = CounterEmphasis.SECONDARY,
                ),
            ),
        ),
    ),
    MeditationCatalogEntry(
        id = "autocompasion_5",
        titleRes = R.string.meditation_catalog_autocompasion_5_title,
        descriptionRes = R.string.meditation_catalog_autocompasion_5_description,
        categoryRes = R.string.meditation_catalog_category_autocompasion,
        icon = Icons.Filled.Favorite,
        approxDurationMinutes = 5,
        access = ContentAccess.Free,
        definition = { selfCompassionMeditationDefinition(SelfCompassionLength.SHORT) },
        presentation = MeditationPresentation(
            textResources = mapOf(
                SelfCompassionText.INTRO to R.string.meditation_selfcompassion_intro,
                BreathingText.INHALE to R.string.guided_meditation_inhale,
                BreathingText.EXHALE to R.string.guided_meditation_exhale,
                SelfCompassionText.PHRASE_1 to R.string.meditation_selfcompassion_phrase_1,
                SelfCompassionText.PHRASE_2 to R.string.meditation_selfcompassion_phrase_2,
                SelfCompassionText.CLOSING to R.string.meditation_selfcompassion_closing,
            ),
            audioResources = emptyMap(),
            counters = listOf(
                MeditationCounter(
                    repeatId = "sc_breathing",
                    total = 10,
                    labelRes = R.string.guided_meditation_breath_label,
                    emphasis = CounterEmphasis.SECONDARY,
                ),
            ),
        ),
    ),
    MeditationCatalogEntry(
        id = "autocompasion_10",
        titleRes = R.string.meditation_catalog_autocompasion_10_title,
        descriptionRes = R.string.meditation_catalog_autocompasion_10_description,
        categoryRes = R.string.meditation_catalog_category_autocompasion,
        icon = Icons.Filled.Favorite,
        approxDurationMinutes = 10,
        access = ContentAccess.Pro,
        definition = { selfCompassionMeditationDefinition(SelfCompassionLength.LONG) },
        presentation = MeditationPresentation(
            textResources = mapOf(
                SelfCompassionText.INTRO to R.string.meditation_selfcompassion_intro,
                BreathingText.INHALE to R.string.guided_meditation_inhale,
                BreathingText.EXHALE to R.string.guided_meditation_exhale,
                SelfCompassionText.PHRASE_1 to R.string.meditation_selfcompassion_phrase_1,
                SelfCompassionText.PHRASE_2 to R.string.meditation_selfcompassion_phrase_2,
                SelfCompassionText.PHRASE_3 to R.string.meditation_selfcompassion_phrase_3,
                SelfCompassionText.CLOSING to R.string.meditation_selfcompassion_closing,
            ),
            audioResources = emptyMap(),
            counters = listOf(
                MeditationCounter(
                    repeatId = "sc_breathing",
                    total = 16,
                    labelRes = R.string.guided_meditation_breath_label,
                    emphasis = CounterEmphasis.SECONDARY,
                ),
            ),
        ),
    ),
)

/** `null` for an unknown id is the fail-safe contract REQ-5.4.3 depends on. */
fun findMeditationCatalogEntry(id: String): MeditationCatalogEntry? =
    meditationCatalog().firstOrNull { it.id == id }
