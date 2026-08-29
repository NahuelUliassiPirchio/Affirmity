package com.pirxhio.affirmity.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** One affirmation parsed out of a pasted JSON import, prior to any DB/image-download work. */
data class ParsedAffirmation(
    val title: String,
    val subtitle: String,
    val backgroundType: String,
    val backgroundValue: String,
)

/**
 * Parses a pasted JSON array of affirmations (see README "Affirmation data format").
 *
 * Pure/JVM-testable: no Android Context or DAO dependency. Throws
 * [IllegalArgumentException] with a user-presentable message on any structural problem; an empty
 * array is valid and yields an empty list.
 *
 * Authors MAY embed `[token]` bracket syntax in `title`/`subtitle` to mark a placeholder the user
 * can tap and override (see [com.pirxhio.affirmity.data.AffirmationTemplateParser]). Brackets are
 * NOT interpreted here — [ParsedAffirmation] carries the raw authored text unchanged; parsing into
 * segments/tokens happens downstream, at render/save time, never during import.
 */
fun parseAffirmationsJson(json: String): List<ParsedAffirmation> {
    val root = try {
        JSONArray(json)
    } catch (e: JSONException) {
        throw IllegalArgumentException("El texto pegado no es un JSON válido.", e)
    }

    return (0 until root.length()).map { index ->
        val element = root.get(index)
        val item = element as? JSONObject
            ?: throw IllegalArgumentException("El elemento ${index + 1} no es un objeto JSON.")

        val title = item.optString("title").trim()
        if (title.isEmpty()) {
            throw IllegalArgumentException("El elemento ${index + 1} no tiene un \"title\" válido.")
        }

        val subtitle = item.optString("subtitle").trim()
        if (subtitle.isEmpty()) {
            throw IllegalArgumentException("El elemento ${index + 1} no tiene un \"subtitle\" válido.")
        }

        val background = item.optJSONObject("background")
            ?: throw IllegalArgumentException("El elemento ${index + 1} no tiene un \"background\" válido.")

        val backgroundType = background.optString("type").trim()
        if (backgroundType != "color" && backgroundType != "image") {
            throw IllegalArgumentException(
                "El elemento ${index + 1} tiene un \"background.type\" inválido (debe ser \"color\" o \"image\")."
            )
        }

        val backgroundValue = background.optString("value").trim()
        if (backgroundValue.isEmpty()) {
            throw IllegalArgumentException("El elemento ${index + 1} no tiene un \"background.value\" válido.")
        }

        ParsedAffirmation(
            title = title,
            subtitle = subtitle,
            backgroundType = backgroundType,
            backgroundValue = backgroundValue,
        )
    }
}
