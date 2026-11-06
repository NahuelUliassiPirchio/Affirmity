package com.pirxhio.affirmity.data

/**
 * Locally persisted milestone bookkeeping. Both fields are null until the first streak value has
 * been observed (see [StreakMilestoneDetector.evaluate] seeding); 0 means "seeded, none reached".
 */
data class MilestoneProgress(
    /** Highest milestone the user has already been celebrated for (or silently caught up to). */
    val highestCelebrated: Int? = null,
    /** Lifetime best milestone reached (any of [StreakMilestones.ALL], including 1); drives badges. */
    val bestReached: Int? = null,
)

data class MilestoneDecision(
    /** Milestone to celebrate now, or null. */
    val celebrate: Int?,
    val updated: MilestoneProgress,
)

/** Pure milestone-celebration decision, no I/O. */
object StreakMilestoneDetector {

    /** Milestones that get a full-screen celebration; 1 day is badge-only. */
    val CELEBRATED: List<Int> = StreakMilestones.ALL.filter { it > 1 }

    /**
     * A milestone may celebrate only on a genuine +1 caused by completing today on an already
     * running flow. Any other jump (cache-then-server catch-up, sign-in, midnight re-subscription
     * where the count stays equal) is caught up silently.
     */
    fun isGenuineStreakIncrement(previous: StreakHealerState?, current: StreakHealerState): Boolean =
        previous != null &&
            current.isTodayDone &&
            current.generalStreakDays == previous.generalStreakDays + 1

    /** A healer grant celebrates only for exactly +1 on an already running flow. */
    fun isGenuineHealerGrant(previous: StreakHealerState?, current: StreakHealerState): Boolean =
        previous != null && current.healerCount == previous.healerCount + 1

    /**
     * @param canCelebrate false for the first emission of a (re)started streak flow and for emissions
     * caused only by a day change: the state is still caught up silently so nothing replays later.
     */
    fun evaluate(stored: MilestoneProgress, streakDays: Int, canCelebrate: Boolean): MilestoneDecision {
        val reached = StreakMilestones.highestReached(streakDays) ?: 0
        val best = maxOf(stored.bestReached ?: 0, reached)
        val celebrated = stored.highestCelebrated
        if (celebrated == null) {
            // Fresh install, first run after the update or a new device: adopt without celebrating.
            return MilestoneDecision(null, MilestoneProgress(reached, best))
        }
        val celebrate = reached.takeIf { canCelebrate && it in CELEBRATED && it > celebrated }
        val newCelebrated = when {
            celebrate != null -> celebrate
            !canCelebrate -> maxOf(celebrated, reached)
            else -> celebrated
        }
        return MilestoneDecision(celebrate, MilestoneProgress(newCelebrated, best))
    }
}
