package com.pirxhio.affirmity.ui.affirmations

import java.io.File

/**
 * Owns the FileProvider-exposed cache directory for "share as image" PNGs. Every [prepare] call
 * removes only shares older than [STALE_AFTER_MS]: a file another app may still be reading through a
 * chooser opened moments ago must survive a second share.
 */
class ShareImageCache(
    private val dir: File,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun prepare(affirmationId: String): File {
        dir.mkdirs()
        dir.listFiles { f -> f.isFile && f.extension == "png" }?.forEach {
            if (it.lastModified() < now() - STALE_AFTER_MS) it.delete()
        }
        val safeId = affirmationId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(48)
        return File(dir, "affirmity_${safeId}_${now()}.png")
    }

    companion object {
        const val STALE_AFTER_MS = 10 * 60 * 1000L

        /** Must match the `<cache-path>` entry in res/xml/file_paths.xml. */
        const val SUBDIR = "shared_images"
    }
}
