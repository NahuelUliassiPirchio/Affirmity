package com.pirxhio.affirmity.diagnostics

enum class SyncFailureKind(val wireName: String) {
    RETRY_LOOP("retry_loop"),
    TERMINAL_UPLOAD("terminal_upload"),
    CLAIM("claim"),
    ACTIVITY_SAVE("activity_save"),
    PAGE_STALL("page_stall"),
}
