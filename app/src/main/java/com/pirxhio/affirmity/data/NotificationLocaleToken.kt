package com.pirxhio.affirmity.data

/**
 * Maps a BCP-47 language tag (e.g. `"en-US"`) to the wire `locale` token the notification server
 * understands (`"en"` / `"es"`). Anything else resolves to `"es"`, mirroring the server's own
 * fallback and the app's default (unqualified) resources.
 */
internal fun notificationLocaleToken(languageTag: String?): String =
    if (languageTag?.substringBefore('-')?.lowercase() == "en") "en" else "es"
