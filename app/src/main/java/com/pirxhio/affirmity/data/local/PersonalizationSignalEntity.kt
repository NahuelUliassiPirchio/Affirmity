package com.pirxhio.affirmity.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * One row per raw behavior event (spec personalization-signals "Local signal storage", design D2)
 * -- an append-only log, weights are NEVER persisted here (they live only in
 * `com.pirxhio.affirmity.personalization.scoring.ScoringWeights`, so they can be retuned without a
 * migration). [signalType] stores [com.pirxhio.affirmity.personalization.signal.SignalType.name].
 */
@Entity(
    tableName = "personalization_signals",
    indices = [Index("occurredAtMillis")],
)
data class PersonalizationSignalEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val signalType: String,
    val themeId: String?,
    val groupId: String?,
    val tone: String?,
    val occurredAtMillis: Long,
)
