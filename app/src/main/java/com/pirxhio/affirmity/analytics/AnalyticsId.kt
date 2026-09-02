package com.pirxhio.affirmity.analytics

import com.pirxhio.affirmity.access.ContentKey
import com.pirxhio.affirmity.ui.groups.AffirmationGroup
import com.pirxhio.affirmity.ui.groups.CatalogTheme
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
        fun of(theme: CatalogTheme): AnalyticsId = AnalyticsId(theme.id)

        /** Notifications V2 (design §9): the copy catalog's `variant_key` is an unbounded `String`
         *  over the wire (server-authored, ~55 keys today, more added via Firestore console) --
         *  unlike every other factory above, it is NOT a compile-time-known constant, so it cannot
         *  be trusted verbatim. Only a closed, harmless shape (`^[a-z0-9_]{1,40}$`, matching the
         *  catalog's own authored key convention) is accepted; anything else -- including free text
         *  that could leak PII if a variant key were ever malformed or spoofed -- maps to
         *  `"unknown"` rather than crossing the [AnalyticsEvent] boundary raw. */
        fun ofNotificationVariant(raw: String): AnalyticsId =
            AnalyticsId(if (NOTIFICATION_VARIANT_KEY_PATTERN.matches(raw)) raw else "unknown")

        private val NOTIFICATION_VARIANT_KEY_PATTERN = Regex("^[a-z0-9_]{1,40}$")
    }
}
