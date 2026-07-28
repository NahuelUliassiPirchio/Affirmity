package com.pirxhio.affirmity.data.local

import android.content.Context
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Caches a background image under [Context.filesDir] — downloaded from a URL or copied from a
 * gallery pick — per the README decision: the persisted affirmation references the local file
 * path, not the remote URL/content Uri (the latter's grant is short-lived anyway).
 */
class AffirmationImageStore(private val context: Context) {

    suspend fun download(url: String): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.connect()
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "HTTP ${connection.responseCode}"
            }

            val extension = url.substringAfterLast('.', missingDelimiterValue = "jpg")
                .substringBefore('?')
                .takeIf { it.length in 1..4 } ?: "jpg"
            val file = newImageFile(extension)

            connection.inputStream.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file.absolutePath
        } finally {
            connection.disconnect()
        }
    }

    suspend fun importFromGallery(uri: Uri): String = withContext(Dispatchers.IO) {
        val file = newImageFile("jpg")
        val input = requireNotNull(context.contentResolver.openInputStream(uri)) {
            "Could not open $uri"
        }
        input.use { stream ->
            file.outputStream().use { output -> stream.copyTo(output) }
        }
        file.absolutePath
    }

    private fun newImageFile(extension: String): File {
        val imagesDir = File(context.filesDir, "affirmation_images").apply { mkdirs() }
        return File(imagesDir, "${UUID.randomUUID()}.$extension")
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
    }
}
