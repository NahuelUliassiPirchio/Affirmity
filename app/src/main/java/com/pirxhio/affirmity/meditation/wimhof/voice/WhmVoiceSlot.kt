package com.pirxhio.affirmity.meditation.wimhof.voice

/**
 * The single voice slot of the Wim Hof guide: at most one clip plays at a time. Required clips that
 * arrive while it is busy wait in a FIFO queue; optional clips are never queued (the director
 * checks [isBusy] and skips them).
 */
class WhmVoiceSlot {
    var current: String? = null
        private set

    private val queue = ArrayDeque<String>()

    val isBusy: Boolean get() = current != null
    val queued: List<String> get() = queue.toList()

    fun start(id: String) {
        current = id
    }

    fun enqueue(id: String) {
        queue.addLast(id)
    }

    /**
     * [id] finished playing. Returns the next queued clip (already made current) or null. An [id]
     * that is not the current clip is stale and ignored.
     */
    fun complete(id: String): String? {
        if (id != current) return null
        current = queue.removeFirstOrNull()
        return current
    }

    /** Frees the slot and drops the queue (section change, stop, end). Returns the clip it cut off, if any. */
    fun release(): String? {
        val interrupted = current
        current = null
        queue.clear()
        return interrupted
    }
}
