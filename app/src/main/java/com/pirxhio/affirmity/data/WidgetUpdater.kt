package com.pirxhio.affirmity.data

/** Pushes a Glance `updateAll` for the weekly tracker widget. Kept context-free/testable. */
fun interface WidgetUpdater {
    suspend fun refresh()
}
