package com.pirxhio.affirmity.data.local

import androidx.room.TypeConverter
import org.json.JSONObject

/**
 * Room converter for [AffirmationEntity.overrides] (D7). Deterministic hand-built JSON over a
 * sorted key set — `org.json` is already a production dependency (see [AffirmationImport]) and
 * sorting avoids the non-deterministic key order of `JSONObject(map).toString()`.
 */
class OverridesConverters {
    @TypeConverter
    fun fromOverrides(value: Map<String, String>?): String =
        value.orEmpty().toSortedMap().entries.joinToString(
            separator = ",",
            prefix = "{",
            postfix = "}",
        ) { (k, v) -> "${JSONObject.quote(k)}:${JSONObject.quote(v)}" }

    @TypeConverter
    fun toOverrides(value: String?): Map<String, String> = runCatching {
        val obj = JSONObject(value.orEmpty().ifBlank { "{}" })
        obj.keys().asSequence()
            .mapNotNull { k -> obj.optString(k).takeIf { it.isNotBlank() }?.let { k to it } }
            .toMap()
    }.getOrDefault(emptyMap()) // malformed column content degrades to "no overrides", never crashes
}
