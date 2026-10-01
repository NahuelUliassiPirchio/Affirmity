package com.pirxhio.affirmity.ui.theme

import androidx.compose.ui.graphics.Color
import com.pirxhio.affirmity.data.DEFAULT_COLLECTION_HIGHLIGHT_ID

/**
 * Highlight colours a user can give a group ("Your groups" design, 7b). Each value is the sRGB
 * conversion of the design's oklch swatch (L/C/h in the comments). [id] is what gets persisted, so
 * it must never change; the colour may be retuned freely.
 */
enum class GroupHighlight(val id: String, val color: Color) {
    Teal("teal", Color(0xFF5AB3C8)), // oklch(0.72 0.09 215)
    Violet("violet", Color(0xFFA48FE1)), // oklch(0.70 0.12 295)
    Amber("amber", Color(0xFFED914C)), // oklch(0.74 0.14 55)
    Rose("rose", Color(0xFFE3839A)), // oklch(0.72 0.12 5)
    Green("green", Color(0xFF7FC581)), // oklch(0.76 0.12 145)
    Gold("gold", Color(0xFFDEC26D)), // oklch(0.82 0.11 92)
    ;

    companion object {
        val Default = Teal

        /** Unknown or missing ids (e.g. written by a newer build) degrade to [Default]. */
        fun fromId(id: String?): GroupHighlight = entries.firstOrNull { it.id == id } ?: Default
    }
}
