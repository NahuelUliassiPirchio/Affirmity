package com.pirxhio.affirmity.personalization.goals

import androidx.annotation.StringRes
import com.pirxhio.affirmity.R

/**
 * A stable personalization goal, deliberately separate from catalog taxonomy. [id] is what gets
 * persisted; [labelRes] is the localized user-facing label and must be resolved in the UI.
 */
data class UserGoal(
    val id: String,
    @StringRes val labelRes: Int,
) {
    companion object {
        val all: List<UserGoal> = listOf(
            UserGoal(id = "calm", labelRes = R.string.user_goal_calm),
            UserGoal(id = "confidence", labelRes = R.string.user_goal_confidence),
            UserGoal(id = "self_love", labelRes = R.string.user_goal_self_love),
            UserGoal(id = "motivation", labelRes = R.string.user_goal_motivation),
            UserGoal(id = "connection", labelRes = R.string.user_goal_connection),
            UserGoal(id = "change", labelRes = R.string.user_goal_change),
            UserGoal(id = "direction", labelRes = R.string.user_goal_direction),
        )
    }
}
