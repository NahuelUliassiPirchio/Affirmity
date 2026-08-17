package com.pirxhio.affirmity.analytics

import com.pirxhio.affirmity.access.ContentKey
import com.pirxhio.affirmity.ui.groups.AffirmationGroup
import com.pirxhio.affirmity.ui.meditation.catalog.MeditationCatalogEntry

/**
 * The ONLY identifier type an [AnalyticsEvent] may carry (design D2). The constructor is private;
 * there is no `AnalyticsId(String)` a call site can reach. Every factory maps a verified authored
 * constant (never user input) — passing an affirmation title or similar free text does not compile.
 */
@JvmInline
value class AnalyticsId private constructor(val value: String) {
    companion object {
        fun of(key: ContentKey): AnalyticsId = AnalyticsId(key.storageKey)
        fun of(entry: MeditationCatalogEntry): AnalyticsId = AnalyticsId(entry.id)
        fun of(group: AffirmationGroup): AnalyticsId = AnalyticsId(group.id)
    }
}
