package com.pirxhio.affirmity.personalization.signal

/**
 * A single raw behavior event (spec "Local signal storage", design D2). Plain data -- no Android
 * dependency -- so it can be constructed and scored (see
 * [com.pirxhio.affirmity.personalization.scoring.PersonalizationScoring]) without touching Room.
 */
data class PersonalizationSignal(
    val type: SignalType,
    val themeId: String?,
    val groupId: String?,
    val tone: String?,
    val occurredAtMillis: Long,
)
