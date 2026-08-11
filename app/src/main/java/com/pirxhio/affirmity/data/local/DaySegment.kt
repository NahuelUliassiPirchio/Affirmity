package com.pirxhio.affirmity.data.local

/**
 * The four fixed, user-selectable day-part segments a notification channel may fire within.
 * [key] is the stable persistence string (DataStore set entries, Firestore array entries) —
 * decoupled from the enum constant name so a future rename doesn't silently change stored data,
 * same pattern as [com.pirxhio.affirmity.notifications.NotificationChannelSpec.prefsPrefix].
 * Mirrors `functions/src/schedule.ts`'s `DAY_SEGMENTS` map — the [key] values must match exactly.
 */
enum class DaySegment(val startMinute: Int, val endMinute: Int, val key: String) {
    MADRUGADA(0, 360, "madrugada"),
    MANANA(360, 720, "manana"),
    TARDE(720, 1080, "tarde"),
    NOCHE(1080, 1440, "noche"),
}
