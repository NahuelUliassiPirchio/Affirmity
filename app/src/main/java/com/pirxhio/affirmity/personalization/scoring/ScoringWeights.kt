package com.pirxhio.affirmity.personalization.scoring

import com.pirxhio.affirmity.personalization.signal.SignalType

/**
 * Base weights per [SignalType], before decay. Overridable independently of
 * [SignalType.defaultWeight] so weights can be retuned without touching the enum or requiring a
 * migration -- signals are stored raw, never as a precomputed score (design D2/D4).
 */
data class ScoringWeights(val weightByType: Map<SignalType, Int>) {
    fun weightFor(type: SignalType): Int = weightByType[type] ?: type.defaultWeight

    companion object {
        val Default = ScoringWeights(SignalType.entries.associateWith { it.defaultWeight })
    }
}
