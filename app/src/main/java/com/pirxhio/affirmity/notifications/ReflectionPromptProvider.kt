package com.pirxhio.affirmity.notifications

import android.content.Context
import androidx.annotation.ArrayRes
import com.pirxhio.affirmity.R
import java.util.Calendar
import kotlin.random.Random

/** Picks a random reflection-prompt variant for the current weekday, so the fallback body
 * (used when the FCM payload doesn't override it) reads as "today, Monday, ..." and changes
 * day to day instead of always being the same static string. */
object ReflectionPromptProvider {

    @ArrayRes
    private fun promptsFor(dayOfWeek: Int): Int = when (dayOfWeek) {
        Calendar.SUNDAY -> R.array.reflection_prompts_sunday
        Calendar.MONDAY -> R.array.reflection_prompts_monday
        Calendar.TUESDAY -> R.array.reflection_prompts_tuesday
        Calendar.WEDNESDAY -> R.array.reflection_prompts_wednesday
        Calendar.THURSDAY -> R.array.reflection_prompts_thursday
        Calendar.FRIDAY -> R.array.reflection_prompts_friday
        else -> R.array.reflection_prompts_saturday
    }

    fun randomPrompt(context: Context, calendar: Calendar = Calendar.getInstance()): String {
        val prompts = context.resources.getStringArray(promptsFor(calendar.get(Calendar.DAY_OF_WEEK)))
        return prompts[Random.nextInt(prompts.size)]
    }
}
