package com.pirxhio.affirmity.ui.affirmations

import java.io.File

/**
 * Owns the FileProvider-exposed cache directory for "share as image" PNGs. Every [prepare] call
 * wipes previous shares first, so the cache holds at most the file currently being shared.
 */
class ShareImageCache(private val dir: File) {
    fun prepare(affirmationId: String): File {
        dir.mkdirs()
        dir.listFiles { f -> f.isFile && f.extension == "png" }?.forEach { it.delete() }
        val safeId = affirmationId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(48)
        return File(dir, "affirmity_${safeId}_${System.currentTimeMillis()}.png")
    }

    companion object {
        /** Must match the `<cache-path>` entry in res/xml/file_paths.xml. */
        const val SUBDIR = "shared_images"
    }
}
