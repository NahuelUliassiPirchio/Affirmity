package com.pirxhio.affirmity.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pirxhio.affirmity.notifications.NotificationChannelSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.notificationDebugDataStore by preferencesDataStore(name = "notification_debug_log")

/** One row in the debug trail: what happened, to which channel, and when. */
data class NotificationLogEntry(
    val timestampMillis: Long,
    val channel: String,
    val event: NotificationLogEvent,
    val detail: String = "",
)

enum class NotificationLogEvent(val label: String) {
    SCHEDULED("Próximo disparo programado"),
    WORKER_STARTED("Worker arrancó"),
    SKIPPED_CHANNEL_DISABLED("Saltado: canal desactivado"),
    NOTIFY_POSTED("Notificación enviada al sistema"),
    NOTIFY_SKIPPED_PERMISSION("Bloqueada: permiso/canal desactivado en el SO"),
    WORKER_FAILED("Worker falló (se reintentará)"),
}

/**
 * Small ring-buffer trail of what the notification pipeline actually did, so a "no me llegó" report
 * can be answered without guessing: did WorkManager even run, did it try to post, did the OS block it.
 * Persisted in DataStore (survives process death) as delimited text — no JSON dependency needed for
 * a handful of short, controlled-vocabulary fields.
 */
class NotificationDebugLog(private val context: Context) {

    val entries: Flow<List<NotificationLogEntry>> =
        context.notificationDebugDataStore.data.map { prefs -> decode(prefs[LOG_KEY].orEmpty()) }

    suspend fun record(channel: NotificationChannelSpec, event: NotificationLogEvent, detail: String = "") {
        context.notificationDebugDataStore.edit { prefs ->
            val current = decode(prefs[LOG_KEY].orEmpty())
            val entry = NotificationLogEntry(System.currentTimeMillis(), channel.name, event, detail)
            prefs[LOG_KEY] = encode((listOf(entry) + current).take(MAX_ENTRIES))
        }
    }

    suspend fun clear() {
        context.notificationDebugDataStore.edit { it.remove(LOG_KEY) }
    }

    private fun encode(entries: List<NotificationLogEntry>): String = entries.joinToString(RECORD_SEP) {
        listOf(it.timestampMillis.toString(), it.channel, it.event.name, it.detail).joinToString(FIELD_SEP)
    }

    private fun decode(raw: String): List<NotificationLogEntry> {
        if (raw.isEmpty()) return emptyList()
        return raw.split(RECORD_SEP).mapNotNull { record ->
            val fields = record.split(FIELD_SEP)
            if (fields.size != 4) return@mapNotNull null
            val event = runCatching { NotificationLogEvent.valueOf(fields[2]) }.getOrNull() ?: return@mapNotNull null
            NotificationLogEntry(
                timestampMillis = fields[0].toLongOrNull() ?: return@mapNotNull null,
                channel = fields[1],
                event = event,
                detail = fields[3],
            )
        }
    }

    private companion object {
        val LOG_KEY = stringPreferencesKey("log")
        const val MAX_ENTRIES = 100
        const val FIELD_SEP = ""
        const val RECORD_SEP = ""
    }
}
