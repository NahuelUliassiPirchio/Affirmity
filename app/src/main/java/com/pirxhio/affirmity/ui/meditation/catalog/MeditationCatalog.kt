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
import com.pirxhio.affirmity.meditation.anapanasati.AnapanasatiConfig
import com.pirxhio.affirmity.meditation.anapanasati.AnapanasatiText
import com.pirxhio.affirmity.meditation.anapanasati.anapanasatiMeditationDefinition
import com.pirxhio.affirmity.meditation.bhramari.BhramariConfig
import com.pirxhio.affirmity.meditation.bhramari.BhramariText
import com.pirxhio.affirmity.meditation.bhramari.bhramariMeditationDefinition
import com.pirxhio.affirmity.meditation.bodyscan.BodyScanConfig
import com.pirxhio.affirmity.meditation.bodyscan.BodyScanText
import com.pirxhio.affirmity.meditation.bodyscan.bodyScanMeditationDefinition
import com.pirxhio.affirmity.meditation.boxbreathing.BoxBreathingConfig
import com.pirxhio.affirmity.meditation.boxbreathing.BoxBreathingText
import com.pirxhio.affirmity.meditation.boxbreathing.boxBreathingMeditationDefinition
import com.pirxhio.affirmity.meditation.breathing.BreathingText
import com.pirxhio.affirmity.meditation.breathingaffirmations.BreathingAffirmationsConfig
import com.pirxhio.affirmity.meditation.breathingaffirmations.BreathingAffirmationsText
import com.pirxhio.affirmity.meditation.breathingaffirmations.breathingAffirmationsMeditationDefinition
import com.pirxhio.affirmity.meditation.breathing478.Breathing478Config
import com.pirxhio.affirmity.meditation.breathing478.Breathing478Text
import com.pirxhio.affirmity.meditation.breathing478.breathing478MeditationDefinition
import com.pirxhio.affirmity.meditation.breathoffire.BreathOfFireConfig
import com.pirxhio.affirmity.meditation.breathoffire.BreathOfFireText
import com.pirxhio.affirmity.meditation.breathoffire.breathOfFireMeditationDefinition
import com.pirxhio.affirmity.meditation.calm.CalmText
import com.pirxhio.affirmity.meditation.calm.calmMeditationDefinition
import com.pirxhio.affirmity.meditation.customization.CustomizationField
import com.pirxhio.affirmity.meditation.customization.decodeMultiSelect
import com.pirxhio.affirmity.meditation.centeringprayer.CenteringPrayerConfig
import com.pirxhio.affirmity.meditation.centeringprayer.CenteringPrayerText
import com.pirxhio.affirmity.meditation.centeringprayer.centeringPrayerMeditationDefinition
import com.pirxhio.affirmity.meditation.coherentbreathing.CoherentBreathingConfig
import com.pirxhio.affirmity.meditation.coherentbreathing.coherentBreathingMeditationDefinition
import com.pirxhio.affirmity.meditation.dhikr.DhikrConfig
import com.pirxhio.affirmity.meditation.dhikr.DhikrText
import com.pirxhio.affirmity.meditation.dhikr.dhikrMeditationDefinition
import com.pirxhio.affirmity.meditation.extendedexhale.ExtendedExhaleConfig
import com.pirxhio.affirmity.meditation.extendedexhale.extendedExhaleMeditationDefinition
import com.pirxhio.affirmity.meditation.focus.FocusText
import com.pirxhio.affirmity.meditation.focus.focusMeditationDefinition
import com.pirxhio.affirmity.meditation.gratitudemeditation.GratitudeConfig
import com.pirxhio.affirmity.meditation.gratitudemeditation.GratitudeMeditationText
import com.pirxhio.affirmity.meditation.gratitudemeditation.gratitudeMeditationDefinition
import com.pirxhio.affirmity.meditation.hitbodedut.HitbodedutConfig
import com.pirxhio.affirmity.meditation.hitbodedut.HitbodedutText
import com.pirxhio.affirmity.meditation.hitbodedut.hitbodedutMeditationDefinition
import com.pirxhio.affirmity.meditation.jesusprayer.JesusPrayerConfig
import com.pirxhio.affirmity.meditation.jesusprayer.JesusPrayerText
import com.pirxhio.affirmity.meditation.jesusprayer.jesusPrayerMeditationDefinition
import com.pirxhio.affirmity.meditation.kapalabhati.KapalabhatiConfig
import com.pirxhio.affirmity.meditation.kapalabhati.KapalabhatiText
import com.pirxhio.affirmity.meditation.kapalabhati.kapalabhatiMeditationDefinition
import com.pirxhio.affirmity.meditation.lectiodivina.LectioDivinaConfig
import com.pirxhio.affirmity.meditation.lectiodivina.LectioDivinaText
import com.pirxhio.affirmity.meditation.lectiodivina.lectioDivinaMeditationDefinition
import com.pirxhio.affirmity.meditation.mantra.MantraConfig
import com.pirxhio.affirmity.meditation.mantra.MantraMeditationText
import com.pirxhio.affirmity.meditation.mantra.mantraMeditationDefinition
import com.pirxhio.affirmity.meditation.metta.MettaConfig
import com.pirxhio.affirmity.meditation.metta.MettaText
import com.pirxhio.affirmity.meditation.metta.mettaMeditationDefinition
import com.pirxhio.affirmity.meditation.muraqabah.MuraqabahConfig
import com.pirxhio.affirmity.meditation.muraqabah.MuraqabahText
import com.pirxhio.affirmity.meditation.muraqabah.muraqabahMeditationDefinition
import com.pirxhio.affirmity.meditation.nadishodhana.NadiShodhanaConfig
import com.pirxhio.affirmity.meditation.nadishodhana.NadiShodhanaText
import com.pirxhio.affirmity.meditation.nadishodhana.nadiShodhanaMeditationDefinition
import com.pirxhio.affirmity.meditation.noting.NotingConfig
import com.pirxhio.affirmity.meditation.noting.NotingText
import com.pirxhio.affirmity.meditation.noting.notingMeditationDefinition
import com.pirxhio.affirmity.meditation.om.OmConfig
import com.pirxhio.affirmity.meditation.om.OmMeditationText
import com.pirxhio.affirmity.meditation.om.omMeditationDefinition
import com.pirxhio.affirmity.meditation.openawareness.OpenAwarenessConfig
import com.pirxhio.affirmity.meditation.openawareness.OpenAwarenessText
import com.pirxhio.affirmity.meditation.openawareness.openAwarenessMeditationDefinition
import com.pirxhio.affirmity.meditation.progressivemusclerelaxation.ProgressiveMuscleRelaxationConfig
import com.pirxhio.affirmity.meditation.progressivemusclerelaxation.ProgressiveMuscleRelaxationText
import com.pirxhio.affirmity.meditation.progressivemusclerelaxation.progressiveMuscleRelaxationMeditationDefinition
import com.pirxhio.affirmity.meditation.quickreset.QuickResetText
import com.pirxhio.affirmity.meditation.quickreset.quickResetMeditationDefinition
import com.pirxhio.affirmity.meditation.selfcompassion.SelfCompassionLength
import com.pirxhio.affirmity.meditation.selfcompassion.SelfCompassionText
import com.pirxhio.affirmity.meditation.selfcompassion.selfCompassionMeditationDefinition
import com.pirxhio.affirmity.meditation.selfcompassionbreak.SelfCompassionBreakConfig
import com.pirxhio.affirmity.meditation.selfcompassionbreak.SelfCompassionBreakText
import com.pirxhio.affirmity.meditation.selfcompassionbreak.selfCompassionBreakMeditationDefinition
import com.pirxhio.affirmity.meditation.sleep.SleepText
import com.pirxhio.affirmity.meditation.sleep.sleepMeditationDefinition
import com.pirxhio.affirmity.meditation.sohum.SoHumConfig
import com.pirxhio.affirmity.meditation.sohum.SoHumText
import com.pirxhio.affirmity.meditation.sohum.soHumMeditationDefinition
import com.pirxhio.affirmity.meditation.trataka.TratakaConfig
import com.pirxhio.affirmity.meditation.trataka.TratakaText
import com.pirxhio.affirmity.meditation.trataka.tratakaMeditationDefinition
import com.pirxhio.affirmity.meditation.vipassana.VipassanaConfig
import com.pirxhio.affirmity.meditation.vipassana.VipassanaText
import com.pirxhio.affirmity.meditation.vipassana.vipassanaMeditationDefinition
import com.pirxhio.affirmity.meditation.visualization.VisualizationConfig
import com.pirxhio.affirmity.meditation.visualization.VisualizationText
import com.pirxhio.affirmity.meditation.visualization.visualizationMeditationDefinition
import com.pirxhio.affirmity.meditation.walking.WalkingConfig
import com.pirxhio.affirmity.meditation.walking.WalkingMeditationText
import com.pirxhio.affirmity.meditation.walking.walkingMeditationDefinition
import com.pirxhio.affirmity.meditation.yoganidra.YogaNidraConfig
import com.pirxhio.affirmity.meditation.yoganidra.YogaNidraText
import com.pirxhio.affirmity.meditation.yoganidra.yogaNidraMeditationDefinition
import com.pirxhio.affirmity.meditation.zazen.ZazenConfig
import com.pirxhio.affirmity.meditation.zazen.ZazenText
import com.pirxhio.affirmity.meditation.zazen.zazenMeditationDefinition
import com.pirxhio.affirmity.ui.groups.defaultAffirmationGroups

/**
 * The meditation registry (REQ-4.6). No DI, no persistence, no remote fetch — mirrors
 * `com.pirxhio.affirmity.ui.groups.selectableAffirmationGroups`. Entry order and access companions
 * are exactly the REQ-4.6 table; `MeditationCatalogTest` assertion #1 pins both.
 *
 * Every entry's `presentation.audioResources` is `emptyMap()` (design §7.3, acceptance criterion
 * 3): all entries ship silent, `PlayVoice`/`StartAmbient` ids stay symbolic until a real asset
 * is authored. `presentation.textResources` maps every `ShowText` id reachable from the entry's
 * own definition tree — including the shared [BreathingText.INHALE]/[BreathingText.EXHALE] ids
 * every entry emits via `breathingBlock()` — to the same strings the breathing demo already uses.
 *
 * The original 7 entries are REQ-4.6's pinned set (order + access companions asserted by
 * `MeditationCatalogTest` assertion #1). The 8 breathing-technique entries, the 8
 * mindfulness/silence/movement/mantra entries, and the 4 prayer/contemplation entries
 * (dhikr, muraqabah, hitbodedut, self_compassion_break) appended after them are later
 * additions — appended, not interleaved, to keep that pinned prefix stable.
 */
/**
 * Shared `durationMinutes` [CustomizationField.Options] builder, reused by every entry whose only
 * duration knob is "pick a session length" (mindfulness/mantra customization onward) -- keeps the
 * option-value string resources (`meditation_customization_duration_N`) shared instead of
 * duplicated per entry.
 */
private fun durationMinutesField(default: Int, options: List<Int>) = CustomizationField.Options(
    key = "durationMinutes",
    labelRes = R.string.meditation_customization_duration_minutes,
    default = default,
    options = options,
    optionLabelRes = { option -> minuteValueRes(option) },
)

/** Shared per-value minute label, reused by every `durationMinutes`-shaped field (mindfulness/
 * mantra customization onward) -- extends the `_Nmin` set the breathing-family customization
 * started for `coherent_breathing`. */
private fun minuteValueRes(minutes: Int): Int = when (minutes) {
    1 -> R.string.meditation_customization_duration_1min
    2 -> R.string.meditation_customization_duration_2min
    3 -> R.string.meditation_customization_duration_3min
    5 -> R.string.meditation_customization_duration_5min
    7 -> R.string.meditation_customization_duration_7min
    10 -> R.string.meditation_customization_duration_10min
    13 -> R.string.meditation_customization_duration_13min
    15 -> R.string.meditation_customization_duration_15min
    20 -> R.string.meditation_customization_duration_20min
    30 -> R.string.meditation_customization_duration_30min
    45 -> R.string.meditation_customization_duration_45min
    else -> R.string.meditation_customization_duration_60min
}

/** Shared per-value seconds label, reused by second-granularity fields (body-awareness
 * customization). */
private fun secondsValueRes(seconds: Int): Int = when (seconds) {
    30 -> R.string.meditation_customization_seconds_30
    60 -> R.string.meditation_customization_seconds_60
    90 -> R.string.meditation_customization_seconds_90
    180 -> R.string.meditation_customization_seconds_180
    else -> R.string.meditation_customization_seconds_120
}

/** Shared `guidanceLevel` ("full"/"light"/"silent") field, reused across entries. */
private fun guidanceLevelField(default: String, options: List<String>) = CustomizationField.Options(
    key = "guidanceLevel",
    labelRes = R.string.meditation_customization_guidance_level,
    default = default,
    options = options,
    optionLabelRes = { option ->
        when (option) {
            "light" -> R.string.meditation_customization_guidance_light
            "silent" -> R.string.meditation_customization_guidance_silent
            else -> R.string.meditation_customization_guidance_full
        }
    },
)

/** Reads the affirmation texts a caller (`MainActivity`'s pre-session async enrichment step, the
 * one place this project fetches runtime content before calling `entry.definition(...)`) encoded
 * into the config map under `"affirmationText.0"`, `"affirmationText.1"`, ... in order -- an
 * indexed-key scheme rather than one delimited string, since affirmation text is free-form user
 * content that could itself contain [CustomizationField.MultiSelect.SEPARATOR]. Absent entirely
 * (a no-fields test build, or before the async fetch resolves) yields an empty list, which
 * [breathingAffirmationsMeditationDefinition] already handles with its own fallback cue. */
private fun affirmationTextsFromConfig(config: Map<String, String>): List<String> =
    generateSequence(0) { it + 1 }
        .map { config["affirmationText.$it"] }
        .takeWhile { it != null }
        .filterNotNull()
        .toList()

fun meditationCatalog(): List<MeditationCatalogEntry> = listOf(
    MeditationCatalogEntry(
        id = "reset_rapido",
        titleRes = R.string.meditation_catalog_reset_rapido_title,
        descriptionRes = R.string.meditation_catalog_reset_rapido_description,
        categoryRes = R.string.meditation_catalog_category_relajacion,
        icon = Icons.Filled.Bolt,
        approxDurationMinutes = 2,
        access = ContentAccess.Free,
        definition = { _ -> quickResetMeditationDefinition() },
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
        definition = { _ -> calmMeditationDefinition() },
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
        definition = { _ -> focusMeditationDefinition() },
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
        definition = { _ -> sleepMeditationDefinition() },
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
        definition = { config ->
            val durationMinutes = config["durationMinutes"]?.toIntOrNull() ?: 10
            val perRegionMillis = ((durationMinutes * 60_000L - 120_000L) / 6).coerceAtLeast(10_000L)
            bodyScanMeditationDefinition(
                BodyScanConfig(
                    perRegionMillis = perRegionMillis,
                    direction = config["direction"] ?: "feet_to_head",
                    detailLevel = config["detailLevel"] ?: "standard",
                ),
            )
        },
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
        customizationFields = listOf(
            durationMinutesField(default = 10, options = listOf(5, 10, 15, 20, 30)),
            CustomizationField.Options(
                key = "direction",
                labelRes = R.string.meditation_customization_bodyscan_direction,
                default = "feet_to_head",
                options = listOf("feet_to_head", "head_to_feet"),
                optionLabelRes = { option ->
                    if (option == "head_to_feet") {
                        R.string.meditation_customization_bodyscan_direction_head_to_feet
                    } else {
                        R.string.meditation_customization_bodyscan_direction_feet_to_head
                    }
                },
            ),
            CustomizationField.Options(
                key = "detailLevel",
                labelRes = R.string.meditation_customization_bodyscan_detail_level,
                default = "standard",
                options = listOf("quick", "standard", "detailed"),
                optionLabelRes = { option ->
                    when (option) {
                        "quick" -> R.string.meditation_customization_bodyscan_detail_quick
                        "detailed" -> R.string.meditation_customization_bodyscan_detail_detailed
                        else -> R.string.meditation_customization_bodyscan_detail_standard
                    }
                },
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
        definition = { _ -> selfCompassionMeditationDefinition(SelfCompassionLength.SHORT) },
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
        definition = { _ -> selfCompassionMeditationDefinition(SelfCompassionLength.LONG) },
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
    MeditationCatalogEntry(
        id = "box_breathing",
        titleRes = R.string.meditation_catalog_box_breathing_title,
        descriptionRes = R.string.meditation_catalog_box_breathing_description,
        categoryRes = R.string.meditation_catalog_category_respiracion,
        icon = Icons.Filled.Spa,
        approxDurationMinutes = 2,
        access = ContentAccess.Free,
        definition = { config ->
            boxBreathingMeditationDefinition(
                BoxBreathingConfig(
                    rounds = config["rounds"]?.toIntOrNull() ?: 6,
                    inhaleMillis = (config["inhaleSeconds"]?.toLongOrNull() ?: 4L) * 1_000L,
                    holdFullMillis = (config["holdFullSeconds"]?.toLongOrNull() ?: 4L) * 1_000L,
                    exhaleMillis = (config["exhaleSeconds"]?.toLongOrNull() ?: 4L) * 1_000L,
                    holdEmptyMillis = (config["holdEmptySeconds"]?.toLongOrNull() ?: 4L) * 1_000L,
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                BreathingText.INHALE to R.string.guided_meditation_inhale,
                BoxBreathingText.HOLD to R.string.meditation_boxbreathing_hold,
                BreathingText.EXHALE to R.string.guided_meditation_exhale,
            ),
            audioResources = emptyMap(),
            counters = listOf(
                MeditationCounter(
                    repeatId = "breathing",
                    total = 6,
                    labelRes = R.string.guided_meditation_breath_label,
                    emphasis = CounterEmphasis.SECONDARY,
                ),
            ),
        ),
        customizationFields = listOf(
            CustomizationField.IntSlider(
                key = "rounds",
                labelRes = R.string.meditation_customization_boxbreathing_rounds,
                default = 6,
                min = 3,
                max = 30,
            ),
            CustomizationField.IntSlider(
                key = "inhaleSeconds",
                labelRes = R.string.meditation_customization_boxbreathing_inhale_seconds,
                default = 4,
                min = 2,
                max = 8,
            ),
            CustomizationField.IntSlider(
                key = "holdFullSeconds",
                labelRes = R.string.meditation_customization_boxbreathing_hold_full_seconds,
                default = 4,
                min = 0,
                max = 8,
            ),
            CustomizationField.IntSlider(
                key = "exhaleSeconds",
                labelRes = R.string.meditation_customization_boxbreathing_exhale_seconds,
                default = 4,
                min = 2,
                max = 10,
            ),
            CustomizationField.IntSlider(
                key = "holdEmptySeconds",
                labelRes = R.string.meditation_customization_boxbreathing_hold_empty_seconds,
                default = 4,
                min = 0,
                max = 8,
            ),
        ),
    ),
    MeditationCatalogEntry(
        id = "breathing_4_7_8",
        titleRes = R.string.meditation_catalog_breathing478_title,
        descriptionRes = R.string.meditation_catalog_breathing478_description,
        categoryRes = R.string.meditation_catalog_category_respiracion,
        icon = Icons.Filled.Bedtime,
        approxDurationMinutes = 1,
        access = ContentAccess.Free,
        definition = { config ->
            val pace = config["paceMultiplier"]?.toFloatOrNull() ?: 1f
            breathing478MeditationDefinition(
                Breathing478Config(
                    rounds = config["rounds"]?.toIntOrNull() ?: 4,
                    inhaleMillis = (4_000L * pace).toLong(),
                    holdMillis = (7_000L * pace).toLong(),
                    exhaleMillis = (8_000L * pace).toLong(),
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                BreathingText.INHALE to R.string.guided_meditation_inhale,
                Breathing478Text.HOLD to R.string.meditation_breathing478_hold,
                BreathingText.EXHALE to R.string.guided_meditation_exhale,
            ),
            audioResources = emptyMap(),
            counters = listOf(
                MeditationCounter(
                    repeatId = "breathing",
                    total = 4,
                    labelRes = R.string.guided_meditation_breath_label,
                    emphasis = CounterEmphasis.SECONDARY,
                ),
            ),
        ),
        customizationFields = listOf(
            CustomizationField.IntSlider(
                key = "rounds",
                labelRes = R.string.meditation_customization_breathing478_rounds,
                default = 4,
                min = 2,
                max = 10,
            ),
            CustomizationField.Options(
                key = "paceMultiplier",
                labelRes = R.string.meditation_customization_breathing478_pace,
                default = 1f,
                options = listOf(0.75f, 1f, 1.25f),
                optionLabelRes = { option ->
                    when (option) {
                        0.75f -> R.string.meditation_customization_breathing478_pace_slower
                        1.25f -> R.string.meditation_customization_breathing478_pace_faster
                        else -> R.string.meditation_customization_breathing478_pace_normal
                    }
                },
            ),
        ),
    ),
    MeditationCatalogEntry(
        id = "extended_exhale",
        titleRes = R.string.meditation_catalog_extended_exhale_title,
        descriptionRes = R.string.meditation_catalog_extended_exhale_description,
        categoryRes = R.string.meditation_catalog_category_respiracion,
        icon = Icons.Filled.Spa,
        approxDurationMinutes = 2,
        access = ContentAccess.ProOrAdPerUse,
        definition = { config ->
            extendedExhaleMeditationDefinition(
                ExtendedExhaleConfig(
                    rounds = config["rounds"]?.toIntOrNull() ?: 10,
                    inhaleMillis = (config["inhaleSeconds"]?.toLongOrNull() ?: 4L) * 1_000L,
                    exhaleMillis = (config["exhaleSeconds"]?.toLongOrNull() ?: 6L) * 1_000L,
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                BreathingText.INHALE to R.string.guided_meditation_inhale,
                BreathingText.EXHALE to R.string.guided_meditation_exhale,
            ),
            audioResources = emptyMap(),
            counters = listOf(
                MeditationCounter(
                    repeatId = "breathing",
                    total = 10,
                    labelRes = R.string.guided_meditation_breath_label,
                    emphasis = CounterEmphasis.SECONDARY,
                ),
            ),
        ),
        customizationFields = listOf(
            CustomizationField.IntSlider(
                key = "rounds",
                labelRes = R.string.meditation_customization_extendedexhale_rounds,
                default = 10,
                min = 3,
                max = 40,
            ),
            CustomizationField.IntSlider(
                key = "inhaleSeconds",
                labelRes = R.string.meditation_customization_extendedexhale_inhale_seconds,
                default = 4,
                min = 2,
                max = 6,
            ),
            CustomizationField.IntSlider(
                key = "exhaleSeconds",
                labelRes = R.string.meditation_customization_extendedexhale_exhale_seconds,
                default = 6,
                min = 4,
                max = 12,
            ),
        ),
    ),
    MeditationCatalogEntry(
        id = "coherent_breathing",
        titleRes = R.string.meditation_catalog_coherent_breathing_title,
        descriptionRes = R.string.meditation_catalog_coherent_breathing_description,
        categoryRes = R.string.meditation_catalog_category_respiracion,
        icon = Icons.Filled.CenterFocusStrong,
        approxDurationMinutes = 5,
        access = ContentAccess.Pro,
        definition = { config ->
            coherentBreathingMeditationDefinition(
                CoherentBreathingConfig(
                    durationMinutes = config["durationMinutes"]?.toIntOrNull() ?: 5,
                    breathsPerMinute = config["breathsPerMinute"]?.toIntOrNull() ?: 6,
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                BreathingText.INHALE to R.string.guided_meditation_inhale,
                BreathingText.EXHALE to R.string.guided_meditation_exhale,
            ),
            audioResources = emptyMap(),
            counters = listOf(
                MeditationCounter(
                    repeatId = "breathing",
                    total = 30,
                    labelRes = R.string.guided_meditation_breath_label,
                    emphasis = CounterEmphasis.SECONDARY,
                ),
            ),
        ),
        customizationFields = listOf(
            CustomizationField.Options(
                key = "durationMinutes",
                labelRes = R.string.meditation_customization_coherentbreathing_duration_minutes,
                default = 5,
                options = listOf(3, 5, 10, 15, 20),
                optionLabelRes = { option ->
                    when (option) {
                        3 -> R.string.meditation_customization_duration_3min
                        10 -> R.string.meditation_customization_duration_10min
                        15 -> R.string.meditation_customization_duration_15min
                        20 -> R.string.meditation_customization_duration_20min
                        else -> R.string.meditation_customization_duration_5min
                    }
                },
            ),
            CustomizationField.IntSlider(
                key = "breathsPerMinute",
                labelRes = R.string.meditation_customization_coherentbreathing_breaths_per_minute,
                default = 6,
                min = 4,
                max = 7,
            ),
        ),
    ),
    MeditationCatalogEntry(
        id = "nadi_shodhana",
        titleRes = R.string.meditation_catalog_nadi_shodhana_title,
        descriptionRes = R.string.meditation_catalog_nadi_shodhana_description,
        categoryRes = R.string.meditation_catalog_category_respiracion,
        icon = Icons.Filled.CenterFocusStrong,
        approxDurationMinutes = 2,
        access = ContentAccess.Pro,
        definition = { config ->
            nadiShodhanaMeditationDefinition(
                NadiShodhanaConfig(
                    rounds = config["rounds"]?.toIntOrNull() ?: 6,
                    breathMillis = (config["breathSeconds"]?.toLongOrNull() ?: 4L) * 1_000L,
                    retention = config["retention"]?.toBooleanStrictOrNull() ?: false,
                    retentionMillis = (config["retentionSeconds"]?.toLongOrNull() ?: 4L) * 1_000L,
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                NadiShodhanaText.INHALE_LEFT to R.string.meditation_nadishodhana_inhale_left,
                NadiShodhanaText.EXHALE_RIGHT to R.string.meditation_nadishodhana_exhale_right,
                NadiShodhanaText.INHALE_RIGHT to R.string.meditation_nadishodhana_inhale_right,
                NadiShodhanaText.EXHALE_LEFT to R.string.meditation_nadishodhana_exhale_left,
                NadiShodhanaText.HOLD to R.string.meditation_nadishodhana_hold,
            ),
            audioResources = emptyMap(),
            counters = listOf(
                MeditationCounter(
                    repeatId = "breathing",
                    total = 6,
                    labelRes = R.string.guided_meditation_breath_label,
                    emphasis = CounterEmphasis.SECONDARY,
                ),
            ),
        ),
        customizationFields = listOf(
            CustomizationField.IntSlider(
                key = "rounds",
                labelRes = R.string.meditation_customization_nadishodhana_rounds,
                default = 6,
                min = 2,
                max = 20,
            ),
            CustomizationField.IntSlider(
                key = "breathSeconds",
                labelRes = R.string.meditation_customization_nadishodhana_breath_seconds,
                default = 4,
                min = 3,
                max = 8,
            ),
            CustomizationField.Toggle(
                key = "retention",
                labelRes = R.string.meditation_customization_nadishodhana_retention,
                default = false,
            ),
            CustomizationField.IntSlider(
                key = "retentionSeconds",
                labelRes = R.string.meditation_customization_nadishodhana_retention_seconds,
                default = 4,
                min = 0,
                max = 12,
            ),
        ),
    ),
    MeditationCatalogEntry(
        id = "bhramari",
        titleRes = R.string.meditation_catalog_bhramari_title,
        descriptionRes = R.string.meditation_catalog_bhramari_description,
        categoryRes = R.string.meditation_catalog_category_respiracion,
        icon = Icons.Filled.Spa,
        approxDurationMinutes = 1,
        access = ContentAccess.ProOrAdTrial,
        definition = { config ->
            bhramariMeditationDefinition(
                BhramariConfig(
                    rounds = config["rounds"]?.toIntOrNull() ?: 7,
                    inhaleMillis = (config["inhaleSeconds"]?.toLongOrNull() ?: 4L) * 1_000L,
                    exhaleMillis = (config["exhaleSeconds"]?.toLongOrNull() ?: 8L) * 1_000L,
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                BreathingText.INHALE to R.string.guided_meditation_inhale,
                BhramariText.HUMMING_EXHALE to R.string.meditation_bhramari_humming_exhale,
            ),
            audioResources = emptyMap(),
            counters = listOf(
                MeditationCounter(
                    repeatId = "breathing",
                    total = 7,
                    labelRes = R.string.guided_meditation_breath_label,
                    emphasis = CounterEmphasis.SECONDARY,
                ),
            ),
        ),
        customizationFields = listOf(
            CustomizationField.IntSlider(
                key = "rounds",
                labelRes = R.string.meditation_customization_bhramari_rounds,
                default = 7,
                min = 3,
                max = 21,
            ),
            CustomizationField.IntSlider(
                key = "inhaleSeconds",
                labelRes = R.string.meditation_customization_bhramari_inhale_seconds,
                default = 4,
                min = 3,
                max = 6,
            ),
            CustomizationField.IntSlider(
                key = "exhaleSeconds",
                labelRes = R.string.meditation_customization_bhramari_exhale_seconds,
                default = 8,
                min = 5,
                max = 15,
            ),
        ),
    ),
    MeditationCatalogEntry(
        id = "kapalabhati",
        titleRes = R.string.meditation_catalog_kapalabhati_title,
        descriptionRes = R.string.meditation_catalog_kapalabhati_description,
        categoryRes = R.string.meditation_catalog_category_energia,
        icon = Icons.Filled.Bolt,
        approxDurationMinutes = 4,
        access = ContentAccess.Pro,
        definition = { config ->
            kapalabhatiMeditationDefinition(
                KapalabhatiConfig(
                    rounds = config["rounds"]?.toIntOrNull() ?: 3,
                    breathsPerRound = config["breathsPerRound"]?.toIntOrNull() ?: 30,
                    restMillis = (config["restSeconds"]?.toLongOrNull() ?: 30L) * 1_000L,
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                KapalabhatiText.PREPARATION to R.string.meditation_kapalabhati_preparation,
                KapalabhatiText.ACTIVE to R.string.meditation_kapalabhati_active,
            ),
            audioResources = emptyMap(),
            counters = listOf(
                MeditationCounter(
                    repeatId = "rounds",
                    total = 3,
                    labelRes = R.string.guided_meditation_round_label,
                    emphasis = CounterEmphasis.SECONDARY,
                ),
            ),
        ),
        customizationFields = listOf(
            CustomizationField.IntSlider(
                key = "rounds",
                labelRes = R.string.meditation_customization_kapalabhati_rounds,
                default = 3,
                min = 1,
                max = 5,
            ),
            CustomizationField.Options(
                key = "breathsPerRound",
                labelRes = R.string.meditation_customization_kapalabhati_breaths_per_round,
                default = 30,
                options = listOf(20, 30, 50, 60),
                optionLabelRes = { option ->
                    when (option) {
                        20 -> R.string.meditation_customization_kapalabhati_breaths_20
                        50 -> R.string.meditation_customization_kapalabhati_breaths_50
                        60 -> R.string.meditation_customization_kapalabhati_breaths_60
                        else -> R.string.meditation_customization_kapalabhati_breaths_30
                    }
                },
            ),
            CustomizationField.IntSlider(
                key = "restSeconds",
                labelRes = R.string.meditation_customization_kapalabhati_rest_seconds,
                default = 30,
                min = 15,
                max = 90,
            ),
        ),
    ),
    MeditationCatalogEntry(
        id = "breath_of_fire",
        titleRes = R.string.meditation_catalog_breath_of_fire_title,
        descriptionRes = R.string.meditation_catalog_breath_of_fire_description,
        categoryRes = R.string.meditation_catalog_category_energia,
        icon = Icons.Filled.Bolt,
        approxDurationMinutes = 5,
        access = ContentAccess.ProOrAdTrial,
        definition = { config ->
            breathOfFireMeditationDefinition(
                BreathOfFireConfig(
                    rounds = config["rounds"]?.toIntOrNull() ?: 3,
                    activeMillis = (config["activeSeconds"]?.toLongOrNull() ?: 30L) * 1_000L,
                    recoveryMillis = (config["restSeconds"]?.toLongOrNull() ?: 30L) * 1_000L,
                    pace = config["pace"] ?: "beginner",
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                BreathOfFireText.PREPARATION to R.string.meditation_breathoffire_preparation,
                BreathOfFireText.ACTIVE to R.string.meditation_breathoffire_active,
            ),
            audioResources = emptyMap(),
            counters = listOf(
                MeditationCounter(
                    repeatId = "rounds",
                    total = 3,
                    labelRes = R.string.guided_meditation_round_label,
                    emphasis = CounterEmphasis.SECONDARY,
                ),
            ),
        ),
        customizationFields = listOf(
            CustomizationField.IntSlider(
                key = "rounds",
                labelRes = R.string.meditation_customization_breathoffire_rounds,
                default = 3,
                min = 1,
                max = 5,
            ),
            CustomizationField.Options(
                key = "activeSeconds",
                labelRes = R.string.meditation_customization_breathoffire_active_seconds,
                default = 30,
                options = listOf(15, 30, 45, 60),
                optionLabelRes = { option ->
                    when (option) {
                        15 -> R.string.meditation_customization_breathoffire_active_15
                        45 -> R.string.meditation_customization_breathoffire_active_45
                        60 -> R.string.meditation_customization_breathoffire_active_60
                        else -> R.string.meditation_customization_breathoffire_active_30
                    }
                },
            ),
            CustomizationField.Options(
                key = "restSeconds",
                labelRes = R.string.meditation_customization_breathoffire_rest_seconds,
                default = 30,
                options = listOf(20, 30, 45, 60),
                optionLabelRes = { option ->
                    when (option) {
                        20 -> R.string.meditation_customization_breathoffire_rest_20
                        45 -> R.string.meditation_customization_breathoffire_rest_45
                        60 -> R.string.meditation_customization_breathoffire_rest_60
                        else -> R.string.meditation_customization_breathoffire_rest_30
                    }
                },
            ),
            CustomizationField.Options(
                key = "pace",
                labelRes = R.string.meditation_customization_breathoffire_pace,
                default = "beginner",
                options = listOf("beginner", "moderate", "traditional"),
                optionLabelRes = { option ->
                    when (option) {
                        "moderate" -> R.string.meditation_customization_breathoffire_pace_moderate
                        "traditional" -> R.string.meditation_customization_breathoffire_pace_traditional
                        else -> R.string.meditation_customization_breathoffire_pace_beginner
                    }
                },
            ),
        ),
    ),
    MeditationCatalogEntry(
        id = "anapanasati",
        titleRes = R.string.meditation_catalog_anapanasati_title,
        descriptionRes = R.string.meditation_catalog_anapanasati_description,
        categoryRes = R.string.meditation_catalog_category_mindfulness,
        icon = Icons.Filled.CenterFocusStrong,
        approxDurationMinutes = 10,
        access = ContentAccess.Free,
        definition = { config ->
            val durationMinutes = config["durationMinutes"]?.toIntOrNull() ?: 10
            anapanasatiMeditationDefinition(
                AnapanasatiConfig(
                    awarenessMillis = (durationMinutes * 60_000L - 120_000L).coerceAtLeast(60_000L),
                    guidanceLevel = config["guidanceLevel"] ?: "full",
                    reminderIntervalMinutes = config["reminderIntervalMinutes"]?.toIntOrNull() ?: 2,
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                AnapanasatiText.ARRIVAL to R.string.meditation_anapanasati_arrival,
                AnapanasatiText.AWARENESS to R.string.meditation_anapanasati_awareness,
                AnapanasatiText.CLOSING to R.string.meditation_anapanasati_closing,
            ),
            audioResources = emptyMap(),
            counters = emptyList(),
        ),
        customizationFields = listOf(
            durationMinutesField(default = 10, options = listOf(5, 10, 15, 20, 30, 45)),
            guidanceLevelField(default = "full", options = listOf("full", "light", "silent")),
            CustomizationField.Options(
                key = "reminderIntervalMinutes",
                labelRes = R.string.meditation_customization_anapanasati_reminder_interval,
                default = 2,
                options = listOf(1, 2, 3, 5, 0),
                optionLabelRes = { option ->
                    if (option == 0) R.string.meditation_customization_reminder_off else minuteValueRes(option)
                },
            ),
        ),
    ),
    MeditationCatalogEntry(
        id = "vipassana",
        titleRes = R.string.meditation_catalog_vipassana_title,
        descriptionRes = R.string.meditation_catalog_vipassana_description,
        categoryRes = R.string.meditation_catalog_category_mindfulness,
        icon = Icons.Filled.Spa,
        approxDurationMinutes = 15,
        access = ContentAccess.Pro,
        definition = { config ->
            val durationMinutes = config["durationMinutes"]?.toIntOrNull() ?: 15
            val awarenessEachMillis = ((durationMinutes * 60_000L - 240_000L) / 2).coerceAtLeast(60_000L)
            vipassanaMeditationDefinition(
                VipassanaConfig(
                    bodyAwarenessMillis = awarenessEachMillis,
                    openObservationMillis = awarenessEachMillis,
                    guidanceLevel = config["guidanceLevel"] ?: "full",
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                VipassanaText.BREATH_ANCHOR to R.string.meditation_vipassana_breath_anchor,
                VipassanaText.BODY_AWARENESS to R.string.meditation_vipassana_body_awareness,
                VipassanaText.OPEN_OBSERVATION to R.string.meditation_vipassana_open_observation,
                VipassanaText.CLOSING to R.string.meditation_vipassana_closing,
            ),
            audioResources = emptyMap(),
            counters = emptyList(),
        ),
        customizationFields = listOf(
            durationMinutesField(default = 15, options = listOf(10, 15, 20, 30, 45, 60)),
            guidanceLevelField(default = "full", options = listOf("full", "light", "silent")),
        ),
    ),
    MeditationCatalogEntry(
        id = "metta",
        titleRes = R.string.meditation_catalog_metta_title,
        descriptionRes = R.string.meditation_catalog_metta_description,
        categoryRes = R.string.meditation_catalog_category_compasion,
        icon = Icons.Filled.Favorite,
        approxDurationMinutes = 10,
        access = ContentAccess.ProOrAdPerUse,
        definition = { config ->
            val targets = config["targets"]?.let(::decodeMultiSelect)
                ?: setOf("self", "loved_one", "neutral_person", "difficult_person", "all_beings")
            mettaMeditationDefinition(
                MettaConfig(
                    secondsPerTargetMillis = (config["secondsPerTarget"]?.toLongOrNull() ?: 120L) * 1_000L,
                    targets = targets,
                    traditionalLanguage = config["traditionalLanguage"]?.toBooleanStrictOrNull() ?: false,
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                MettaText.SELF to R.string.meditation_metta_self,
                MettaText.LOVED_ONE to R.string.meditation_metta_loved_one,
                MettaText.NEUTRAL_PERSON to R.string.meditation_metta_neutral_person,
                MettaText.DIFFICULT_PERSON to R.string.meditation_metta_difficult_person,
                MettaText.ALL_BEINGS to R.string.meditation_metta_all_beings,
            ),
            audioResources = emptyMap(),
            counters = emptyList(),
        ),
        customizationFields = listOf(
            CustomizationField.MultiSelect(
                key = "targets",
                labelRes = R.string.meditation_customization_metta_targets,
                default = setOf("self", "loved_one", "neutral_person", "difficult_person", "all_beings"),
                options = listOf("self", "loved_one", "neutral_person", "difficult_person", "all_beings"),
                optionLabelRes = { option ->
                    when (option) {
                        "self" -> R.string.meditation_metta_self
                        "loved_one" -> R.string.meditation_metta_loved_one
                        "neutral_person" -> R.string.meditation_metta_neutral_person
                        "difficult_person" -> R.string.meditation_metta_difficult_person
                        else -> R.string.meditation_metta_all_beings
                    }
                },
            ),
            CustomizationField.Options(
                key = "secondsPerTarget",
                labelRes = R.string.meditation_customization_metta_seconds_per_target,
                default = 120,
                options = listOf(60, 90, 120, 180),
                optionLabelRes = { option ->
                    when (option) {
                        60 -> R.string.meditation_customization_seconds_60
                        90 -> R.string.meditation_customization_seconds_90
                        180 -> R.string.meditation_customization_seconds_180
                        else -> R.string.meditation_customization_seconds_120
                    }
                },
            ),
            CustomizationField.Toggle(
                key = "traditionalLanguage",
                labelRes = R.string.meditation_customization_metta_traditional_language,
                default = false,
            ),
        ),
    ),
    MeditationCatalogEntry(
        id = "zazen",
        titleRes = R.string.meditation_catalog_zazen_title,
        descriptionRes = R.string.meditation_catalog_zazen_description,
        categoryRes = R.string.meditation_catalog_category_silencio,
        icon = Icons.Filled.Spa,
        approxDurationMinutes = 10,
        access = ContentAccess.Pro,
        definition = { config ->
            val durationMinutes = config["durationMinutes"]?.toIntOrNull() ?: 10
            val openingInstructions = config["openingInstructions"]?.toBooleanStrictOrNull() ?: true
            val fixedMillis = if (openingInstructions) 60_000L else 0L
            zazenMeditationDefinition(
                ZazenConfig(
                    silenceMillis = (durationMinutes * 60_000L - fixedMillis).coerceAtLeast(60_000L),
                    openingInstructionsEnabled = openingInstructions,
                    intervalBellMinutes = config["intervalBellMinutes"]?.toIntOrNull() ?: 0,
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                ZazenText.POSTURE to R.string.meditation_zazen_posture,
            ),
            audioResources = emptyMap(),
            counters = listOf(
                MeditationCounter(
                    repeatId = "opening_bell",
                    total = 3,
                    labelRes = R.string.guided_meditation_round_label,
                    emphasis = CounterEmphasis.SECONDARY,
                ),
                MeditationCounter(
                    repeatId = "closing_bell",
                    total = 3,
                    labelRes = R.string.guided_meditation_round_label,
                    emphasis = CounterEmphasis.SECONDARY,
                ),
            ),
        ),
        customizationFields = listOf(
            durationMinutesField(default = 10, options = listOf(5, 10, 15, 20, 30, 45, 60)),
            CustomizationField.Options(
                key = "intervalBellMinutes",
                labelRes = R.string.meditation_customization_zazen_interval_bell,
                default = 0,
                options = listOf(0, 5, 10, 15),
                optionLabelRes = { option ->
                    if (option == 0) R.string.meditation_customization_reminder_off else minuteValueRes(option)
                },
            ),
            CustomizationField.Toggle(
                key = "openingInstructions",
                labelRes = R.string.meditation_customization_zazen_opening_instructions,
                default = true,
            ),
        ),
    ),
    MeditationCatalogEntry(
        id = "walking_meditation",
        titleRes = R.string.meditation_catalog_walking_meditation_title,
        descriptionRes = R.string.meditation_catalog_walking_meditation_description,
        categoryRes = R.string.meditation_catalog_category_movimiento,
        icon = Icons.Filled.Accessibility,
        approxDurationMinutes = 10,
        access = ContentAccess.ProOrAdTrial,
        definition = { config ->
            val durationMinutes = config["durationMinutes"]?.toIntOrNull() ?: 10
            walkingMeditationDefinition(
                WalkingConfig(
                    walkingMillis = (durationMinutes * 60_000L - 180_000L).coerceAtLeast(60_000L),
                    pace = config["pace"] ?: "slow",
                    guidanceLevel = config["guidanceLevel"] ?: "full",
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                WalkingMeditationText.ARRIVAL to R.string.meditation_walkingmeditation_arrival,
                WalkingMeditationText.STANDING to R.string.meditation_walkingmeditation_standing,
                WalkingMeditationText.WALKING to R.string.meditation_walkingmeditation_walking,
                WalkingMeditationText.CLOSING to R.string.meditation_walkingmeditation_closing,
            ),
            audioResources = emptyMap(),
            counters = emptyList(),
        ),
        customizationFields = listOf(
            durationMinutesField(default = 10, options = listOf(5, 10, 15, 20, 30)),
            CustomizationField.Options(
                key = "pace",
                labelRes = R.string.meditation_customization_walking_pace,
                default = "slow",
                options = listOf("very_slow", "slow", "natural"),
                optionLabelRes = { option ->
                    when (option) {
                        "very_slow" -> R.string.meditation_customization_walking_pace_very_slow
                        "natural" -> R.string.meditation_customization_walking_pace_natural
                        else -> R.string.meditation_customization_walking_pace_slow
                    }
                },
            ),
            guidanceLevelField(default = "full", options = listOf("full", "light")),
        ),
    ),
    MeditationCatalogEntry(
        id = "mantra_meditation",
        titleRes = R.string.meditation_catalog_mantra_meditation_title,
        descriptionRes = R.string.meditation_catalog_mantra_meditation_description,
        categoryRes = R.string.meditation_catalog_category_mantra,
        icon = Icons.Filled.CenterFocusStrong,
        approxDurationMinutes = 10,
        access = ContentAccess.Free,
        definition = { config ->
            val durationMinutes = config["durationMinutes"]?.toIntOrNull() ?: 10
            mantraMeditationDefinition(
                MantraConfig(
                    mantraMillis = (durationMinutes * 60_000L - 120_000L).coerceAtLeast(60_000L),
                    mantra = config["mantra"] ?: "so_hum",
                    customMantra = config["customMantra"],
                    repetitionMode = config["repetitionMode"] ?: "mental",
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                MantraMeditationText.PREPARATION to R.string.meditation_mantrameditation_preparation,
                MantraMeditationText.MANTRA to R.string.meditation_mantrameditation_mantra,
            ),
            audioResources = emptyMap(),
            counters = emptyList(),
        ),
        customizationFields = listOf(
            durationMinutesField(default = 10, options = listOf(5, 10, 15, 20, 30)),
            CustomizationField.Options(
                key = "mantra",
                labelRes = R.string.meditation_customization_mantra_choice,
                default = "so_hum",
                options = listOf("so_hum", "om", "custom"),
                optionLabelRes = { option ->
                    when (option) {
                        "om" -> R.string.meditation_customization_mantra_om
                        "custom" -> R.string.meditation_customization_mantra_custom
                        else -> R.string.meditation_customization_mantra_so_hum
                    }
                },
            ),
            CustomizationField.FreeText(
                key = "customMantra",
                labelRes = R.string.meditation_customization_mantra_custom_text,
                placeholderRes = R.string.meditation_customization_mantra_custom_placeholder,
            ),
            CustomizationField.Options(
                key = "repetitionMode",
                labelRes = R.string.meditation_customization_repetition_mode,
                default = "mental",
                options = listOf("mental", "whispered", "spoken"),
                optionLabelRes = { option ->
                    when (option) {
                        "whispered" -> R.string.meditation_customization_repetition_whispered
                        "spoken" -> R.string.meditation_customization_repetition_spoken
                        else -> R.string.meditation_customization_repetition_mental
                    }
                },
            ),
        ),
    ),
    MeditationCatalogEntry(
        id = "om_meditation",
        titleRes = R.string.meditation_catalog_om_meditation_title,
        descriptionRes = R.string.meditation_catalog_om_meditation_description,
        categoryRes = R.string.meditation_catalog_category_mantra,
        icon = Icons.Filled.CenterFocusStrong,
        approxDurationMinutes = 2,
        access = ContentAccess.ProOrAdTrial,
        definition = { config ->
            omMeditationDefinition(
                OmConfig(
                    rounds = config["rounds"]?.toIntOrNull() ?: 12,
                    chantMillis = (config["exhaleSeconds"]?.toLongOrNull() ?: 8L) * 1_000L,
                    chantMode = config["chantMode"] ?: "spoken",
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                BreathingText.INHALE to R.string.guided_meditation_inhale,
                OmMeditationText.CHANT to R.string.meditation_ommeditation_chant,
            ),
            audioResources = emptyMap(),
            counters = listOf(
                MeditationCounter(
                    repeatId = "rounds",
                    total = 12,
                    labelRes = R.string.guided_meditation_round_label,
                    emphasis = CounterEmphasis.SECONDARY,
                ),
            ),
        ),
        customizationFields = listOf(
            CustomizationField.Options(
                key = "rounds",
                labelRes = R.string.meditation_customization_om_rounds,
                default = 12,
                options = listOf(3, 6, 9, 12, 21, 27),
                optionLabelRes = { option ->
                    when (option) {
                        3 -> R.string.meditation_customization_rounds_3
                        6 -> R.string.meditation_customization_rounds_6
                        9 -> R.string.meditation_customization_rounds_9
                        21 -> R.string.meditation_customization_rounds_21
                        27 -> R.string.meditation_customization_rounds_27
                        else -> R.string.meditation_customization_rounds_12
                    }
                },
            ),
            CustomizationField.Options(
                key = "chantMode",
                labelRes = R.string.meditation_customization_om_chant_mode,
                default = "spoken",
                options = listOf("spoken", "whispered", "mental"),
                optionLabelRes = { option ->
                    when (option) {
                        "whispered" -> R.string.meditation_customization_repetition_whispered
                        "mental" -> R.string.meditation_customization_repetition_mental
                        else -> R.string.meditation_customization_repetition_spoken
                    }
                },
            ),
            CustomizationField.IntSlider(
                key = "exhaleSeconds",
                labelRes = R.string.meditation_customization_om_exhale_seconds,
                default = 8,
                min = 5,
                max = 15,
            ),
        ),
    ),
    MeditationCatalogEntry(
        id = "so_hum",
        titleRes = R.string.meditation_catalog_so_hum_title,
        descriptionRes = R.string.meditation_catalog_so_hum_description,
        categoryRes = R.string.meditation_catalog_category_mantra,
        icon = Icons.Filled.Spa,
        approxDurationMinutes = 10,
        access = ContentAccess.Pro,
        definition = { config ->
            val durationMinutes = config["durationMinutes"]?.toIntOrNull() ?: 10
            val paceMultiplier = when (config["breathPace"]) {
                "slow" -> 1.5f
                "very_slow" -> 2f
                else -> 1f
            }
            val inhaleMillis = (4_000L * paceMultiplier).toLong()
            val exhaleMillis = (5_000L * paceMultiplier).toLong()
            val breathMillis = inhaleMillis + exhaleMillis
            val breaths = Math.round((durationMinutes * 60_000L).toDouble() / breathMillis).toInt().coerceAtLeast(1)
            soHumMeditationDefinition(
                SoHumConfig(
                    breaths = breaths,
                    inhaleMillis = inhaleMillis,
                    exhaleMillis = exhaleMillis,
                    mantraVolume = config["mantraVolume"] ?: "mental",
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                SoHumText.SO to R.string.meditation_sohum_so,
                SoHumText.HUM to R.string.meditation_sohum_hum,
            ),
            audioResources = emptyMap(),
            counters = listOf(
                MeditationCounter(
                    repeatId = "breathing",
                    total = 67,
                    labelRes = R.string.guided_meditation_breath_label,
                    emphasis = CounterEmphasis.SECONDARY,
                ),
            ),
        ),
        customizationFields = listOf(
            durationMinutesField(default = 10, options = listOf(5, 10, 15, 20, 30)),
            CustomizationField.Options(
                key = "breathPace",
                labelRes = R.string.meditation_customization_sohum_breath_pace,
                default = "natural",
                options = listOf("natural", "slow", "very_slow"),
                optionLabelRes = { option ->
                    when (option) {
                        "slow" -> R.string.meditation_customization_walking_pace_slow
                        "very_slow" -> R.string.meditation_customization_walking_pace_very_slow
                        else -> R.string.meditation_customization_walking_pace_natural
                    }
                },
            ),
            CustomizationField.Options(
                key = "mantraVolume",
                labelRes = R.string.meditation_customization_sohum_mantra_volume,
                default = "mental",
                options = listOf("mental", "whispered", "spoken"),
                optionLabelRes = { option ->
                    when (option) {
                        "whispered" -> R.string.meditation_customization_repetition_whispered
                        "spoken" -> R.string.meditation_customization_repetition_spoken
                        else -> R.string.meditation_customization_repetition_mental
                    }
                },
            ),
        ),
    ),
    MeditationCatalogEntry(
        id = "dhikr",
        titleRes = R.string.meditation_catalog_dhikr_title,
        descriptionRes = R.string.meditation_catalog_dhikr_description,
        categoryRes = R.string.meditation_catalog_category_oracion,
        icon = Icons.Filled.Spa,
        approxDurationMinutes = 4,
        access = ContentAccess.Free,
        definition = { config ->
            dhikrMeditationDefinition(
                DhikrConfig(
                    repetitions = config["repetitions"]?.toIntOrNull() ?: 33,
                    dhikrPhrase = config["dhikrPhrase"],
                    repetitionMode = config["repetitionMode"] ?: "spoken",
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                DhikrText.INTENTION to R.string.meditation_dhikr_intention,
                DhikrText.REPETITION to R.string.meditation_dhikr_repetition,
            ),
            audioResources = emptyMap(),
            // `total` is pinned to the `repetitions` default (33, assertion 7 only checks
            // `entry.definition(emptyMap())`). A session customized to 11 or 99 repetitions still
            // runs the correct count -- only the live "x of 33" label lags the real total, since
            // `presentation` isn't itself a function of `config` (unlike `definition`). Fixing that
            // display gap is future work, not fabricated here.
            counters = listOf(
                MeditationCounter(
                    repeatId = "repetition",
                    total = 33,
                    labelRes = R.string.guided_meditation_round_label,
                    emphasis = CounterEmphasis.SECONDARY,
                ),
            ),
        ),
        customizationFields = listOf(
            CustomizationField.Options(
                key = "repetitions",
                labelRes = R.string.meditation_customization_dhikr_repetitions,
                default = 33,
                options = listOf(11, 33, 99),
                optionLabelRes = { option ->
                    when (option) {
                        11 -> R.string.meditation_customization_dhikr_repetitions_11
                        99 -> R.string.meditation_customization_dhikr_repetitions_99
                        else -> R.string.meditation_customization_dhikr_repetitions_33
                    }
                },
            ),
            CustomizationField.FreeText(
                key = "dhikrPhrase",
                labelRes = R.string.meditation_customization_dhikr_phrase,
                placeholderRes = R.string.meditation_customization_dhikr_phrase_placeholder,
            ),
            CustomizationField.Options(
                key = "repetitionMode",
                labelRes = R.string.meditation_customization_repetition_mode,
                default = "spoken",
                options = listOf("mental", "quiet", "spoken"),
                optionLabelRes = { option ->
                    when (option) {
                        "quiet" -> R.string.meditation_customization_repetition_quiet
                        "spoken" -> R.string.meditation_customization_repetition_spoken
                        else -> R.string.meditation_customization_repetition_mental
                    }
                },
            ),
        ),
    ),
    MeditationCatalogEntry(
        id = "muraqabah",
        titleRes = R.string.meditation_catalog_muraqabah_title,
        descriptionRes = R.string.meditation_catalog_muraqabah_description,
        categoryRes = R.string.meditation_catalog_category_contemplacion,
        icon = Icons.Filled.Spa,
        approxDurationMinutes = 10,
        access = ContentAccess.ProOrAdPerUse,
        definition = { config ->
            val durationMinutes = config["durationMinutes"]?.toIntOrNull() ?: 10
            muraqabahMeditationDefinition(
                MuraqabahConfig(
                    contemplationMillis = (durationMinutes * 60_000L - 180_000L).coerceAtLeast(60_000L),
                    guidanceLevel = config["guidanceLevel"] ?: "light",
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                MuraqabahText.INTENTION to R.string.meditation_muraqabah_intention,
                MuraqabahText.BREATH_SETTLING to R.string.meditation_muraqabah_breath_settling,
            ),
            audioResources = emptyMap(),
            counters = emptyList(),
        ),
        customizationFields = listOf(
            durationMinutesField(default = 10, options = listOf(5, 10, 15, 20, 30)),
            guidanceLevelField(default = "light", options = listOf("full", "light", "silent")),
        ),
    ),
    MeditationCatalogEntry(
        id = "hitbodedut",
        titleRes = R.string.meditation_catalog_hitbodedut_title,
        descriptionRes = R.string.meditation_catalog_hitbodedut_description,
        categoryRes = R.string.meditation_catalog_category_oracion,
        icon = Icons.Filled.Spa,
        approxDurationMinutes = 13,
        access = ContentAccess.Pro,
        definition = { config ->
            val durationMinutes = config["durationMinutes"]?.toIntOrNull() ?: 13
            val guidedPrompts = config["guidedPrompts"]?.toBooleanStrictOrNull() ?: true
            val promptTopics = config["promptTopics"]?.let(::decodeMultiSelect)
                ?.takeIf { it.isNotEmpty() }
                ?: setOf("gratitude", "concerns", "requests")
            hitbodedutMeditationDefinition(
                HitbodedutConfig(
                    reflectionMillis = (durationMinutes * 60_000L - 600_000L).coerceAtLeast(60_000L),
                    promptTopics = promptTopics.toList(),
                    guidedPromptsEnabled = guidedPrompts,
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                HitbodedutText.ARRIVAL to R.string.meditation_hitbodedut_arrival,
                HitbodedutText.GRATITUDE to R.string.meditation_hitbodedut_gratitude,
                HitbodedutText.CONCERNS to R.string.meditation_hitbodedut_concerns,
                HitbodedutText.REQUESTS to R.string.meditation_hitbodedut_requests,
                HitbodedutText.RELATIONSHIPS to R.string.meditation_hitbodedut_relationships,
                HitbodedutText.PURPOSE to R.string.meditation_hitbodedut_purpose,
                HitbodedutText.FORGIVENESS to R.string.meditation_hitbodedut_forgiveness,
                HitbodedutText.PERSONAL_PRAYER to R.string.meditation_hitbodedut_personal_prayer,
            ),
            audioResources = emptyMap(),
            counters = emptyList(),
        ),
        customizationFields = listOf(
            durationMinutesField(default = 13, options = listOf(5, 10, 13, 15, 20, 30, 60)),
            CustomizationField.Toggle(
                key = "guidedPrompts",
                labelRes = R.string.meditation_customization_hitbodedut_guided_prompts,
                default = true,
            ),
            CustomizationField.MultiSelect(
                key = "promptTopics",
                labelRes = R.string.meditation_customization_hitbodedut_prompt_topics,
                default = setOf("gratitude", "concerns", "requests"),
                options = listOf("gratitude", "concerns", "requests", "relationships", "purpose", "forgiveness"),
                optionLabelRes = { option ->
                    when (option) {
                        "gratitude" -> R.string.meditation_customization_hitbodedut_topic_gratitude
                        "concerns" -> R.string.meditation_customization_hitbodedut_topic_concerns
                        "requests" -> R.string.meditation_customization_hitbodedut_topic_requests
                        "relationships" -> R.string.meditation_customization_hitbodedut_topic_relationships
                        "purpose" -> R.string.meditation_customization_hitbodedut_topic_purpose
                        else -> R.string.meditation_customization_hitbodedut_topic_forgiveness
                    }
                },
            ),
        ),
    ),
    MeditationCatalogEntry(
        id = "self_compassion_break",
        titleRes = R.string.meditation_catalog_self_compassion_break_title,
        descriptionRes = R.string.meditation_catalog_self_compassion_break_description,
        categoryRes = R.string.meditation_catalog_category_compasion,
        icon = Icons.Filled.Favorite,
        approxDurationMinutes = 7,
        access = ContentAccess.ProOrAdTrial,
        definition = { config ->
            val durationMinutes = config["durationMinutes"]?.toIntOrNull() ?: 7
            val totalMillis = durationMinutes * 60_000L
            // recognize/sharedHumanity/kindness keep the spec's 90s/90s/180s (1:1:2) ratio,
            // integration stays fixed at 60s.
            val variableMillis = (totalMillis - 60_000L).coerceAtLeast(80_000L)
            selfCompassionBreakMeditationDefinition(
                SelfCompassionBreakConfig(
                    recognizeMillis = variableMillis / 4,
                    sharedHumanityMillis = variableMillis / 4,
                    kindnessMillis = variableMillis - 2 * (variableMillis / 4),
                    customPhrase = config["customPhrase"],
                    guidanceLevel = config["guidanceLevel"] ?: "full",
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                SelfCompassionBreakText.RECOGNIZE to R.string.meditation_selfcompassionbreak_recognize,
                SelfCompassionBreakText.SHARED_HUMANITY to R.string.meditation_selfcompassionbreak_shared_humanity,
                SelfCompassionBreakText.KINDNESS to R.string.meditation_selfcompassionbreak_kindness,
            ),
            audioResources = emptyMap(),
            counters = emptyList(),
        ),
        customizationFields = listOf(
            durationMinutesField(default = 7, options = listOf(3, 5, 7, 10, 15)),
            CustomizationField.FreeText(
                key = "customPhrase",
                labelRes = R.string.meditation_customization_self_compassion_break_phrase,
                placeholderRes = R.string.meditation_customization_self_compassion_break_phrase_placeholder,
            ),
            guidanceLevelField(default = "full", options = listOf("full", "light")),
        ),
    ),
    MeditationCatalogEntry(
        id = "trataka",
        titleRes = R.string.meditation_catalog_trataka_title,
        descriptionRes = R.string.meditation_catalog_trataka_description,
        categoryRes = R.string.meditation_catalog_category_enfoque,
        icon = Icons.Filled.CenterFocusStrong,
        approxDurationMinutes = 8,
        access = ContentAccess.ProOrAdPerUse,
        definition = { config ->
            tratakaMeditationDefinition(
                TratakaConfig(
                    rounds = config["rounds"]?.toIntOrNull() ?: 2,
                    focusMillis = (config["focusSeconds"]?.toLongOrNull() ?: 120L) * 1_000L,
                    focusObject = config["focusObject"] ?: "candle",
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                TratakaText.PREPARATION to R.string.meditation_trataka_preparation,
                TratakaText.EXTERNAL_FOCUS to R.string.meditation_trataka_external_focus,
                TratakaText.EYES_CLOSED to R.string.meditation_trataka_eyes_closed,
            ),
            audioResources = emptyMap(),
            counters = listOf(
                MeditationCounter(
                    repeatId = "rounds",
                    total = 2,
                    labelRes = R.string.guided_meditation_round_label,
                    emphasis = CounterEmphasis.SECONDARY,
                ),
            ),
        ),
        customizationFields = listOf(
            CustomizationField.IntSlider(
                key = "rounds",
                labelRes = R.string.meditation_customization_trataka_rounds,
                default = 2,
                min = 1,
                max = 5,
            ),
            CustomizationField.Options(
                key = "focusSeconds",
                labelRes = R.string.meditation_customization_trataka_focus_seconds,
                default = 120,
                options = listOf(30, 60, 90, 120, 180),
                optionLabelRes = { seconds -> secondsValueRes(seconds) },
            ),
            CustomizationField.Options(
                key = "focusObject",
                labelRes = R.string.meditation_customization_trataka_focus_object,
                default = "candle",
                options = listOf("candle", "dot", "symbol"),
                optionLabelRes = { option ->
                    when (option) {
                        "dot" -> R.string.meditation_customization_trataka_focus_object_dot
                        "symbol" -> R.string.meditation_customization_trataka_focus_object_symbol
                        else -> R.string.meditation_customization_trataka_focus_object_candle
                    }
                },
            ),
        ),
    ),
    MeditationCatalogEntry(
        id = "yoganidra",
        titleRes = R.string.meditation_catalog_yoganidra_title,
        descriptionRes = R.string.meditation_catalog_yoganidra_description,
        categoryRes = R.string.meditation_catalog_category_sueno,
        icon = Icons.Filled.Bedtime,
        approxDurationMinutes = 20,
        access = ContentAccess.Pro,
        definition = { config ->
            val durationMinutes = config["durationMinutes"]?.toIntOrNull() ?: 20
            val scale = (durationMinutes * 60_000L).toDouble() / 1_200_000.0
            fun scaled(base: Long) = (base * scale).toLong().coerceAtLeast(1_000L)
            yogaNidraMeditationDefinition(
                YogaNidraConfig(
                    settlingMillis = scaled(120_000L),
                    intentionMillis = scaled(60_000L),
                    bodyRegionMillis = scaled(80_000L),
                    breathAwarenessMillis = scaled(180_000L),
                    visualizationMillis = scaled(240_000L),
                    returnMillis = scaled(120_000L),
                    intention = config["intention"],
                    sleepEnding = config["sleepEnding"]?.toBooleanStrictOrNull() ?: false,
                    voiceGuidance = config["voiceGuidance"] ?: "continuous",
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                YogaNidraText.SETTLING to R.string.meditation_yoganidra_settling,
                YogaNidraText.INTENTION to R.string.meditation_yoganidra_intention,
                YogaNidraText.FEET to R.string.meditation_yoganidra_feet,
                YogaNidraText.LEGS to R.string.meditation_yoganidra_legs,
                YogaNidraText.ABDOMEN to R.string.meditation_yoganidra_abdomen,
                YogaNidraText.CHEST to R.string.meditation_yoganidra_chest,
                YogaNidraText.ARMS to R.string.meditation_yoganidra_arms,
                YogaNidraText.HEAD to R.string.meditation_yoganidra_head,
                YogaNidraText.BREATH_AWARENESS to R.string.meditation_yoganidra_breath_awareness,
                YogaNidraText.VISUALIZATION to R.string.meditation_yoganidra_visualization,
                YogaNidraText.RETURN to R.string.meditation_yoganidra_return,
            ),
            audioResources = emptyMap(),
            counters = emptyList(),
        ),
        customizationFields = listOf(
            durationMinutesField(default = 20, options = listOf(10, 15, 20, 30, 45)),
            CustomizationField.FreeText(
                key = "intention",
                labelRes = R.string.meditation_customization_yoganidra_intention,
                placeholderRes = R.string.meditation_customization_yoganidra_intention_placeholder,
            ),
            CustomizationField.Toggle(
                key = "sleepEnding",
                labelRes = R.string.meditation_customization_yoganidra_sleep_ending,
                default = false,
            ),
            CustomizationField.Options(
                key = "voiceGuidance",
                labelRes = R.string.meditation_customization_yoganidra_voice_guidance,
                default = "continuous",
                options = listOf("continuous", "minimal"),
                optionLabelRes = { option ->
                    if (option == "minimal") {
                        R.string.meditation_customization_yoganidra_voice_guidance_minimal
                    } else {
                        R.string.meditation_customization_yoganidra_voice_guidance_continuous
                    }
                },
            ),
        ),
    ),
    MeditationCatalogEntry(
        id = "open_awareness",
        titleRes = R.string.meditation_catalog_openawareness_title,
        descriptionRes = R.string.meditation_catalog_openawareness_description,
        categoryRes = R.string.meditation_catalog_category_mindfulness,
        icon = Icons.Filled.Spa,
        approxDurationMinutes = 10,
        access = ContentAccess.Free,
        definition = { config ->
            val durationMinutes = config["durationMinutes"]?.toIntOrNull() ?: 10
            openAwarenessMeditationDefinition(
                OpenAwarenessConfig(
                    openAwarenessMillis = (durationMinutes * 60_000L - 180_000L).coerceAtLeast(30_000L),
                    guidanceLevel = config["guidanceLevel"] ?: "light",
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                OpenAwarenessText.ANCHOR to R.string.meditation_openawareness_anchor,
                OpenAwarenessText.EXPAND to R.string.meditation_openawareness_expand,
                OpenAwarenessText.OPEN to R.string.meditation_openawareness_open,
            ),
            audioResources = emptyMap(),
            counters = emptyList(),
        ),
        customizationFields = listOf(
            durationMinutesField(default = 10, options = listOf(5, 10, 15, 20, 30)),
            guidanceLevelField(default = "light", options = listOf("full", "light", "silent")),
        ),
    ),
    MeditationCatalogEntry(
        id = "noting",
        titleRes = R.string.meditation_catalog_noting_title,
        descriptionRes = R.string.meditation_catalog_noting_description,
        categoryRes = R.string.meditation_catalog_category_mindfulness,
        icon = Icons.Filled.CenterFocusStrong,
        approxDurationMinutes = 10,
        access = ContentAccess.ProOrAdTrial,
        definition = { config ->
            val durationMinutes = config["durationMinutes"]?.toIntOrNull() ?: 10
            val labels = config["labels"]?.let(::decodeMultiSelect)
                ?: setOf("thinking", "hearing", "feeling")
            notingMeditationDefinition(
                NotingConfig(
                    notingMillis = (durationMinutes * 60_000L - 180_000L).coerceAtLeast(30_000L),
                    labels = labels,
                    guidanceLevel = config["guidanceLevel"] ?: "full",
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                NotingText.BREATH_ANCHOR to R.string.meditation_noting_breath_anchor,
                NotingText.NOTING to R.string.meditation_noting_noting,
                NotingText.OPEN to R.string.meditation_noting_open,
            ),
            audioResources = emptyMap(),
            counters = emptyList(),
        ),
        customizationFields = listOf(
            durationMinutesField(default = 10, options = listOf(5, 10, 15, 20, 30)),
            CustomizationField.MultiSelect(
                key = "labels",
                labelRes = R.string.meditation_customization_noting_labels,
                default = setOf("thinking", "hearing", "feeling"),
                options = listOf("thinking", "hearing", "feeling", "seeing", "emotion", "planning"),
                optionLabelRes = { option ->
                    when (option) {
                        "hearing" -> R.string.meditation_customization_noting_label_hearing
                        "feeling" -> R.string.meditation_customization_noting_label_feeling
                        "seeing" -> R.string.meditation_customization_noting_label_seeing
                        "emotion" -> R.string.meditation_customization_noting_label_emotion
                        "planning" -> R.string.meditation_customization_noting_label_planning
                        else -> R.string.meditation_customization_noting_label_thinking
                    }
                },
            ),
            guidanceLevelField(default = "full", options = listOf("full", "light")),
        ),
    ),
    MeditationCatalogEntry(
        id = "progressive_muscle_relaxation",
        titleRes = R.string.meditation_catalog_progressivemusclerelaxation_title,
        descriptionRes = R.string.meditation_catalog_progressivemusclerelaxation_description,
        categoryRes = R.string.meditation_catalog_category_cuerpo,
        icon = Icons.Filled.Accessibility,
        approxDurationMinutes = 5,
        access = ContentAccess.Pro,
        definition = { config ->
            val allGroups = listOf("feet", "legs", "abdomen", "hands", "arms", "shoulders", "face")
            val groups = config["muscleGroups"]?.let(::decodeMultiSelect)?.let { selected ->
                allGroups.filter { it in selected }
            } ?: allGroups
            progressiveMuscleRelaxationMeditationDefinition(
                ProgressiveMuscleRelaxationConfig(
                    tenseMillis = (config["tenseSeconds"]?.toLongOrNull() ?: 5L) * 1_000L,
                    relaxMillis = (config["relaxSeconds"]?.toLongOrNull() ?: 15L) * 1_000L,
                    muscleGroups = groups,
                    sleepEnding = config["sleepEnding"]?.toBooleanStrictOrNull() ?: false,
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                ProgressiveMuscleRelaxationText.SETTLING to R.string.meditation_progressivemusclerelaxation_settling,
                ProgressiveMuscleRelaxationText.TENSE to R.string.meditation_progressivemusclerelaxation_tense,
                ProgressiveMuscleRelaxationText.RELAX to R.string.meditation_progressivemusclerelaxation_relax,
            ),
            audioResources = emptyMap(),
            counters = emptyList(),
        ),
        customizationFields = listOf(
            CustomizationField.IntSlider(
                key = "tenseSeconds",
                labelRes = R.string.meditation_customization_pmr_tense_seconds,
                default = 5,
                min = 3,
                max = 8,
            ),
            CustomizationField.IntSlider(
                key = "relaxSeconds",
                labelRes = R.string.meditation_customization_pmr_relax_seconds,
                default = 15,
                min = 10,
                max = 30,
            ),
            CustomizationField.MultiSelect(
                key = "muscleGroups",
                labelRes = R.string.meditation_customization_pmr_muscle_groups,
                default = setOf("feet", "legs", "abdomen", "hands", "arms", "shoulders", "face"),
                options = listOf("feet", "legs", "abdomen", "hands", "arms", "shoulders", "face"),
                optionLabelRes = { option ->
                    when (option) {
                        "legs" -> R.string.meditation_customization_pmr_group_legs
                        "abdomen" -> R.string.meditation_customization_pmr_group_abdomen
                        "hands" -> R.string.meditation_customization_pmr_group_hands
                        "arms" -> R.string.meditation_customization_pmr_group_arms
                        "shoulders" -> R.string.meditation_customization_pmr_group_shoulders
                        "face" -> R.string.meditation_customization_pmr_group_face
                        else -> R.string.meditation_customization_pmr_group_feet
                    }
                },
            ),
            CustomizationField.Toggle(
                key = "sleepEnding",
                labelRes = R.string.meditation_customization_pmr_sleep_ending,
                default = false,
            ),
        ),
    ),
    MeditationCatalogEntry(
        id = "visualization",
        titleRes = R.string.meditation_catalog_visualization_title,
        descriptionRes = R.string.meditation_catalog_visualization_description,
        categoryRes = R.string.meditation_catalog_category_visualizacion,
        icon = Icons.Filled.Spa,
        approxDurationMinutes = 12,
        access = ContentAccess.ProOrAdPerUse,
        definition = { config ->
            val durationMinutes = config["durationMinutes"]?.toIntOrNull() ?: 12
            // relaxation(120s) + integration(120s) + return(60s) stay fixed; visualization absorbs
            // the remainder, preserving the exact 720s (12min) default.
            val visualizationMillis = (durationMinutes * 60_000L - 300_000L).coerceAtLeast(60_000L)
            visualizationMeditationDefinition(
                VisualizationConfig(
                    visualizationMillis = visualizationMillis,
                    scenario = config["scenario"] ?: "nature",
                    backgroundSound = config["backgroundSound"] ?: "none",
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                VisualizationText.RELAXATION to R.string.meditation_visualization_relaxation,
                VisualizationText.VISUALIZATION to R.string.meditation_visualization_visualization,
                VisualizationText.INTEGRATION to R.string.meditation_visualization_integration,
                VisualizationText.RETURN to R.string.meditation_visualization_return,
            ),
            audioResources = emptyMap(),
            counters = emptyList(),
        ),
        customizationFields = listOf(
            durationMinutesField(default = 12, options = listOf(5, 10, 12, 15, 20)),
            CustomizationField.Options(
                key = "scenario",
                labelRes = R.string.meditation_customization_visualization_scenario,
                default = "nature",
                options = listOf("nature", "safe_place", "goal", "performance", "custom"),
                optionLabelRes = { option ->
                    when (option) {
                        "safe_place" -> R.string.meditation_customization_visualization_scenario_safe_place
                        "goal" -> R.string.meditation_customization_visualization_scenario_goal
                        "performance" -> R.string.meditation_customization_visualization_scenario_performance
                        "custom" -> R.string.meditation_customization_visualization_scenario_custom
                        else -> R.string.meditation_customization_visualization_scenario_nature
                    }
                },
            ),
            CustomizationField.Options(
                key = "backgroundSound",
                labelRes = R.string.meditation_customization_visualization_background_sound,
                default = "none",
                options = listOf("none", "nature", "rain", "ocean"),
                optionLabelRes = { option ->
                    when (option) {
                        "nature" -> R.string.meditation_customization_visualization_background_sound_nature
                        "rain" -> R.string.meditation_customization_visualization_background_sound_rain
                        "ocean" -> R.string.meditation_customization_visualization_background_sound_ocean
                        else -> R.string.meditation_customization_visualization_background_sound_none
                    }
                },
            ),
        ),
    ),
    MeditationCatalogEntry(
        id = "gratitude_meditation",
        titleRes = R.string.meditation_catalog_gratitude_meditation_title,
        descriptionRes = R.string.meditation_catalog_gratitude_meditation_description,
        categoryRes = R.string.meditation_catalog_category_gratitud,
        icon = Icons.Filled.Favorite,
        approxDurationMinutes = 7,
        access = ContentAccess.Free,
        definition = { config ->
            val durationMinutes = config["durationMinutes"]?.toIntOrNull() ?: 7
            val promptCount = (config["promptCount"]?.toIntOrNull() ?: 3).coerceIn(1, 3)
            val arrivalMillis = 60_000L
            val totalMillis = durationMinutes * 60_000L
            val perPromptMillis = ((totalMillis - arrivalMillis) / promptCount).coerceAtLeast(30_000L)
            gratitudeMeditationDefinition(
                GratitudeConfig(
                    arrivalMillis = arrivalMillis,
                    personMillis = perPromptMillis,
                    experienceMillis = perPromptMillis,
                    presentMillis = perPromptMillis,
                    promptCount = promptCount,
                    journalAtEnd = config["journalAtEnd"]?.toBooleanStrictOrNull() ?: false,
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                GratitudeMeditationText.ARRIVAL to R.string.meditation_gratitudemeditation_arrival,
                GratitudeMeditationText.PERSON to R.string.meditation_gratitudemeditation_person,
                GratitudeMeditationText.EXPERIENCE to R.string.meditation_gratitudemeditation_experience,
                GratitudeMeditationText.PRESENT to R.string.meditation_gratitudemeditation_present,
            ),
            audioResources = emptyMap(),
            counters = emptyList(),
        ),
        customizationFields = listOf(
            durationMinutesField(default = 7, options = listOf(3, 5, 7, 10, 15)),
            // Spec range is 1-5, kept 1-3 here: only 3 canonical prompts exist in this structure
            // (person/experience/present), same "keep the shipped default, don't fabricate
            // content" precedent Batch C/H applied to hitbodedut's duration default.
            CustomizationField.IntSlider(
                key = "promptCount",
                labelRes = R.string.meditation_customization_gratitude_prompt_count,
                default = 3,
                min = 1,
                max = 3,
            ),
            CustomizationField.Toggle(
                key = "journalAtEnd",
                labelRes = R.string.meditation_customization_gratitude_journal_at_end,
                default = false,
            ),
        ),
    ),
    MeditationCatalogEntry(
        id = "centering_prayer",
        titleRes = R.string.meditation_catalog_centering_prayer_title,
        descriptionRes = R.string.meditation_catalog_centering_prayer_description,
        categoryRes = R.string.meditation_catalog_category_oracion,
        icon = Icons.Filled.Spa,
        approxDurationMinutes = 20,
        access = ContentAccess.Pro,
        definition = { config ->
            val durationMinutes = config["durationMinutes"]?.toIntOrNull() ?: 20
            val includeSacredWord = config["openingGuidance"]?.toBooleanStrictOrNull() ?: true
            val closingMillis = 60_000L
            val sacredWordMillis = if (includeSacredWord) 60_000L else 0L
            val silenceMillis = (durationMinutes * 60_000L - closingMillis - sacredWordMillis)
                .coerceAtLeast(60_000L)
            centeringPrayerMeditationDefinition(
                CenteringPrayerConfig(
                    sacredWordMillis = 60_000L,
                    silenceMillis = silenceMillis,
                    closingMillis = closingMillis,
                    includeSacredWord = includeSacredWord,
                    sacredWord = config["sacredWord"],
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                CenteringPrayerText.SACRED_WORD to R.string.meditation_centeringprayer_sacred_word,
                CenteringPrayerText.CLOSING to R.string.meditation_centeringprayer_closing,
            ),
            audioResources = emptyMap(),
            counters = emptyList(),
        ),
        customizationFields = listOf(
            durationMinutesField(default = 20, options = listOf(5, 10, 15, 20, 30)),
            CustomizationField.FreeText(
                key = "sacredWord",
                labelRes = R.string.meditation_customization_centering_prayer_sacred_word,
                placeholderRes = R.string.meditation_customization_centering_prayer_sacred_word_placeholder,
            ),
            CustomizationField.Toggle(
                key = "openingGuidance",
                labelRes = R.string.meditation_customization_centering_prayer_opening_guidance,
                default = true,
            ),
        ),
    ),
    MeditationCatalogEntry(
        id = "jesus_prayer",
        titleRes = R.string.meditation_catalog_jesus_prayer_title,
        descriptionRes = R.string.meditation_catalog_jesus_prayer_description,
        categoryRes = R.string.meditation_catalog_category_oracion,
        icon = Icons.Filled.CenterFocusStrong,
        approxDurationMinutes = 10,
        access = ContentAccess.ProOrAdTrial,
        definition = { config ->
            val durationMinutes = config["durationMinutes"]?.toIntOrNull() ?: 10
            val inhaleMillis = 5_000L
            val exhaleMillis = 4_000L
            val breathMillis = inhaleMillis + exhaleMillis
            val breaths = Math.round((durationMinutes * 60_000L).toDouble() / breathMillis).toInt()
                .coerceAtLeast(1)
            jesusPrayerMeditationDefinition(
                JesusPrayerConfig(
                    breaths = breaths,
                    inhaleMillis = inhaleMillis,
                    exhaleMillis = exhaleMillis,
                    syncWithBreath = config["syncWithBreath"]?.toBooleanStrictOrNull() ?: true,
                    repetitionMode = config["repetitionMode"] ?: "mental",
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                JesusPrayerText.INHALE to R.string.meditation_jesusprayer_inhale,
                JesusPrayerText.EXHALE to R.string.meditation_jesusprayer_exhale,
            ),
            audioResources = emptyMap(),
            counters = listOf(
                MeditationCounter(
                    repeatId = "breathing",
                    total = 67,
                    labelRes = R.string.guided_meditation_breath_label,
                    emphasis = CounterEmphasis.SECONDARY,
                ),
            ),
        ),
        customizationFields = listOf(
            durationMinutesField(default = 10, options = listOf(5, 10, 15, 20, 30)),
            // syncWithBreath/repetitionMode have no clean structural alternative without inventing
            // new behavior (the definition is inherently a breathingBlock) -- recorded in
            // MeditationDefinition.variables only, same precedent as breath-of-fire's `pace`.
            CustomizationField.Toggle(
                key = "syncWithBreath",
                labelRes = R.string.meditation_customization_jesus_prayer_sync_with_breath,
                default = true,
            ),
            CustomizationField.Options(
                key = "repetitionMode",
                labelRes = R.string.meditation_customization_repetition_mode,
                default = "mental",
                options = listOf("mental", "whispered", "spoken"),
                optionLabelRes = { option ->
                    when (option) {
                        "whispered" -> R.string.meditation_customization_repetition_whispered
                        "spoken" -> R.string.meditation_customization_repetition_spoken
                        else -> R.string.meditation_customization_repetition_mental
                    }
                },
            ),
        ),
    ),
    MeditationCatalogEntry(
        id = "lectio_divina",
        titleRes = R.string.meditation_catalog_lectio_divina_title,
        descriptionRes = R.string.meditation_catalog_lectio_divina_description,
        categoryRes = R.string.meditation_catalog_category_contemplacion,
        icon = Icons.Filled.CenterFocusStrong,
        approxDurationMinutes = 15,
        access = ContentAccess.Pro,
        definition = { config ->
            val lectioMinutes = config["minutesPerStage.lectio"]?.toIntOrNull() ?: 3
            val meditatioMinutes = config["minutesPerStage.meditatio"]?.toIntOrNull() ?: 4
            val oratioMinutes = config["minutesPerStage.oratio"]?.toIntOrNull() ?: 3
            val contemplatioMinutes = config["minutesPerStage.contemplatio"]?.toIntOrNull() ?: 5
            lectioDivinaMeditationDefinition(
                LectioDivinaConfig(
                    lectioMillis = lectioMinutes * 60_000L,
                    meditatioMillis = meditatioMinutes * 60_000L,
                    oratioMillis = oratioMinutes * 60_000L,
                    contemplatioMillis = contemplatioMinutes * 60_000L,
                    passage = config["passage"],
                    guidedPrompts = config["guidedPrompts"]?.toBooleanStrictOrNull() ?: true,
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                LectioDivinaText.LECTIO to R.string.meditation_lectiodivina_lectio,
                LectioDivinaText.MEDITATIO to R.string.meditation_lectiodivina_meditatio,
                LectioDivinaText.ORATIO to R.string.meditation_lectiodivina_oratio,
            ),
            audioResources = emptyMap(),
            counters = emptyList(),
        ),
        customizationFields = listOf(
            CustomizationField.Group(
                key = "minutesPerStage",
                labelRes = R.string.meditation_customization_lectio_divina_minutes_per_stage,
                fields = listOf(
                    CustomizationField.IntSlider(
                        key = "lectio",
                        labelRes = R.string.meditation_customization_lectio_divina_stage_lectio,
                        default = 3,
                        min = 1,
                        max = 10,
                    ),
                    CustomizationField.IntSlider(
                        key = "meditatio",
                        labelRes = R.string.meditation_customization_lectio_divina_stage_meditatio,
                        default = 4,
                        min = 1,
                        max = 10,
                    ),
                    CustomizationField.IntSlider(
                        key = "oratio",
                        labelRes = R.string.meditation_customization_lectio_divina_stage_oratio,
                        default = 3,
                        min = 1,
                        max = 10,
                    ),
                    CustomizationField.IntSlider(
                        key = "contemplatio",
                        labelRes = R.string.meditation_customization_lectio_divina_stage_contemplatio,
                        default = 5,
                        min = 1,
                        max = 15,
                    ),
                ),
            ),
            CustomizationField.FreeText(
                key = "passage",
                labelRes = R.string.meditation_customization_lectio_divina_passage,
                placeholderRes = R.string.meditation_customization_lectio_divina_passage_placeholder,
            ),
            // guidedPrompts: no unguided-cue variant exists for these 4 stages (unlike hitbodedut,
            // there's nothing to fall back to besides the cue text itself) -- recorded in
            // MeditationDefinition.variables only.
            CustomizationField.Toggle(
                key = "guidedPrompts",
                labelRes = R.string.meditation_customization_lectio_divina_guided_prompts,
                default = true,
            ),
        ),
    ),
    MeditationCatalogEntry(
        id = "breathing_affirmations",
        titleRes = R.string.meditation_catalog_breathing_affirmations_title,
        descriptionRes = R.string.meditation_catalog_breathing_affirmations_description,
        categoryRes = R.string.meditation_catalog_category_bienestar,
        icon = Icons.Filled.Favorite,
        approxDurationMinutes = 11,
        access = ContentAccess.Pro,
        // The only entry whose definition lambda reads runtime-fetched content instead of purely
        // catalog-authored cues: "affirmationText.0".."affirmationText.N-1" are populated by
        // MainActivity's pre-session async enrichment step (see BreathingAffirmationsAffirmationSource),
        // strictly before this lambda ever runs -- absent entirely, it degrades to the definition's
        // own empty-list fallback cue rather than crashing, which is also what a zero-arg/test build
        // sees.
        definition = { config ->
            val breathingMinutes = config["breathingMinutes"]?.toIntOrNull() ?: 3
            val meditationMinutes = config["meditationMinutes"]?.toIntOrNull() ?: 5
            val affirmationMinutes = config["affirmationMinutes"]?.toIntOrNull() ?: 2
            breathingAffirmationsMeditationDefinition(
                BreathingAffirmationsConfig(
                    breathingTechnique = config["breathingTechnique"] ?: "coherent_breathing",
                    breathingMillis = breathingMinutes * 60_000L,
                    meditationMillis = meditationMinutes * 60_000L,
                    affirmationMillis = affirmationMinutes * 60_000L,
                    affirmationTexts = affirmationTextsFromConfig(config),
                ),
            )
        },
        presentation = MeditationPresentation(
            textResources = mapOf(
                BreathingText.INHALE to R.string.guided_meditation_inhale,
                BreathingText.EXHALE to R.string.guided_meditation_exhale,
                BreathingAffirmationsText.MEDITATION to R.string.meditation_breathingaffirmations_meditation,
                BreathingAffirmationsText.AFFIRMATION_UNAVAILABLE to R.string.meditation_breathingaffirmations_affirmation_unavailable,
                BoxBreathingText.HOLD to R.string.meditation_boxbreathing_hold,
            ),
            audioResources = emptyMap(),
            counters = emptyList(),
        ),
        customizationFields = listOf(
            CustomizationField.Options(
                key = "breathingTechnique",
                labelRes = R.string.meditation_customization_breathing_affirmations_technique,
                default = "coherent_breathing",
                options = listOf("coherent_breathing", "extended_exhale", "box_breathing"),
                optionLabelRes = { option ->
                    when (option) {
                        "extended_exhale" -> R.string.meditation_customization_breathing_affirmations_technique_extended_exhale
                        "box_breathing" -> R.string.meditation_customization_breathing_affirmations_technique_box
                        else -> R.string.meditation_customization_breathing_affirmations_technique_coherent
                    }
                },
            ),
            CustomizationField.Options(
                key = "breathingMinutes",
                labelRes = R.string.meditation_customization_breathing_affirmations_breathing_minutes,
                default = 3,
                options = listOf(1, 2, 3, 5),
                optionLabelRes = { option -> minuteValueRes(option) },
            ),
            CustomizationField.Options(
                key = "meditationMinutes",
                labelRes = R.string.meditation_customization_breathing_affirmations_meditation_minutes,
                default = 5,
                options = listOf(2, 3, 5, 10),
                optionLabelRes = { option -> minuteValueRes(option) },
            ),
            CustomizationField.Options(
                key = "affirmationMinutes",
                labelRes = R.string.meditation_customization_breathing_affirmations_affirmation_minutes,
                default = 2,
                options = listOf(1, 2, 3, 5),
                optionLabelRes = { option -> minuteValueRes(option) },
            ),
            // affirmationUniverse: no personalization engine exists in this codebase (confirmed by
            // exploration -- grep for "adaptive" found no real hits before this feature), so
            // "adaptive" is an honest stub, not a claim of real adaptivity -- see
            // BreathingAffirmationsAffirmationSource's doc comment for exactly what it falls back to.
            CustomizationField.Options(
                key = "affirmationUniverse",
                labelRes = R.string.meditation_customization_breathing_affirmations_universe,
                default = "adaptive",
                options = listOf("adaptive") + defaultAffirmationGroups().map { it.id },
                optionLabelRes = { option ->
                    if (option == "adaptive") {
                        R.string.meditation_customization_breathing_affirmations_universe_adaptive
                    } else {
                        defaultAffirmationGroups().first { it.id == option }.titleRes
                    }
                },
            ),
            CustomizationField.Options(
                key = "affirmationCount",
                labelRes = R.string.meditation_customization_breathing_affirmations_affirmation_count,
                default = 5,
                options = listOf(3, 5, 7, 10),
                optionLabelRes = { option ->
                    when (option) {
                        3 -> R.string.meditation_customization_affirmation_count_3
                        7 -> R.string.meditation_customization_affirmation_count_7
                        10 -> R.string.meditation_customization_affirmation_count_10
                        else -> R.string.meditation_customization_affirmation_count_5
                    }
                },
            ),
        ),
    ),
)

/** `null` for an unknown id is the fail-safe contract REQ-5.4.3 depends on. */
fun findMeditationCatalogEntry(id: String): MeditationCatalogEntry? =
    meditationCatalog().firstOrNull { it.id == id }
