package com.pirxhio.affirmity.ui.meditation.catalog

import com.pirxhio.affirmity.data.catalog.CatalogLocale
import com.pirxhio.affirmity.meditation.wimhof.voice.VOICE_GUIDE_KEY
import com.pirxhio.affirmity.meditation.wimhof.voice.resolveVoiceGuide

/**
 * The customization a session is actually built from. For an entry with a voice guide the stored
 * toggle is resolved against the language the app shows; every other entry is untouched. The
 * session screen, the list/detail duration and the completion threshold all go through here, so
 * they always describe the same tree.
 */
fun MeditationCatalogEntry.resolveSessionCustomization(
    customization: Map<String, String>,
    locale: CatalogLocale = CatalogLocale.resolve(),
): Map<String, String> =
    if (customizationFields.any { it.key == VOICE_GUIDE_KEY }) resolveVoiceGuide(customization, locale) else customization

/** Whole minutes shown in the list and the detail screen. */
fun MeditationCatalogEntry.displayMinutes(
    customization: Map<String, String>,
    locale: CatalogLocale = CatalogLocale.resolve(),
): Int = displayDurationMinutes(definition(resolveSessionCustomization(customization, locale)), approxDurationMinutes)

/** Expected session length in millis (completion threshold basis). */
fun MeditationCatalogEntry.expectedMillis(
    customization: Map<String, String>,
    locale: CatalogLocale = CatalogLocale.resolve(),
): Long = expectedDurationMillis(definition(resolveSessionCustomization(customization, locale)), approxDurationMinutes)
