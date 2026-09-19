package com.pirxhio.affirmity.personalization.scoring

/**
 * Time-decay bands applied to signal base weights (spec "Time decay bands", design D4). Bounds
 * are inclusive on the recent side: exactly 7 days old is still 100%, exactly 30 days old is
 * still 70%, exactly 90 days old is still 40%; anything older is 20%.
 */
object DecayBands {
    fun multiplierForAgeDays(ageDays: Long): Double = when {
        ageDays <= 7 -> 1.0
        ageDays <= 30 -> 0.7
        ageDays <= 90 -> 0.4
        else -> 0.2
    }
}
