package com.pirxhio.affirmity.data.catalog

import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate

/**
 * Catalog languages the app ships an asset for. Default and fallback is [ES].
 *
 * ONE resolver for the whole app (design D1): the catalog seeder, the Settings pre-seed and the
 * push-token locale all go through [resolve], so they cannot disagree.
 */
enum class CatalogLocale(val tag: String, val assetName: String) {
    ES("es", "catalog.v1.json"),
    EN("en", "catalog.v1.en.json"),
    ;

    companion object {
        /** `en`, `en-GB`, ... -> [EN]; anything else (including null) -> [ES]. */
        fun fromLanguageTag(tag: String?): CatalogLocale =
            if (tag?.substringBefore('-')?.lowercase() == "en") EN else ES

        /**
         * Pure core. A set (non-blank) app tag wins and the device source is NOT consulted, so an
         * unsupported in-app language falls back to [ES] even when the device language is `en`.
         */
        fun resolve(appLanguageTag: String?, deviceLanguage: () -> String?): CatalogLocale =
            if (!appLanguageTag.isNullOrBlank()) fromLanguageTag(appLanguageTag) else fromLanguageTag(deviceLanguage())

        /** Android adapter: current in-app locale, else the system locale. */
        fun resolve(): CatalogLocale {
            val appLocales = AppCompatDelegate.getApplicationLocales()
            val appTag = if (appLocales.isEmpty) null else appLocales[0]?.toLanguageTag()
            return resolve(appTag, ::systemDeviceLanguage)
        }

        /**
         * The device language, independent of any in-app override: `Resources.getSystem()` always
         * reflects the system configuration, unlike `Locale.getDefault()` which can still carry the
         * previous app override on API < 33 until the activity is recreated.
         */
        fun systemDeviceLanguage(): String? = Resources.getSystem().configuration.locales[0]?.language
    }
}
