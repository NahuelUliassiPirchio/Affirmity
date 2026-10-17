package com.pirxhio.affirmity.data

/**
 * Whether the streak-rescue dialog should be offered now. It opens once per day after a dismissal
 * ([dismissedEpochDay] == today), unless the user arrived through the healer notification
 * ([fromHealerNotification]), which always re-offers. Never alongside another full-screen gate
 * ([otherGateActive]: onboarding guide, healer-granted screen, paywall).
 */
fun shouldShowStreakRescue(
    activation: HealerActivation,
    healerCount: Int,
    dismissedEpochDay: Long?,
    todayEpochDay: Long,
    fromHealerNotification: Boolean,
    otherGateActive: Boolean,
): Boolean =
    activation is HealerActivation.Available &&
        healerCount > 0 &&
        !otherGateActive &&
        (fromHealerNotification || dismissedEpochDay != todayEpochDay)

/**
 * A healer notification tap re-offers the rescue only on the day it was tapped, so a tap that
 * arrived while the dialog could not show (no healer, another gate up) never leaks into a later
 * day and undoes a dismissal.
 */
fun rescueNotificationOverride(notificationEpochDay: Long?, todayEpochDay: Long): Boolean =
    notificationEpochDay == todayEpochDay

/**
 * Whether the rescue dialog is mounted. Besides the derived [offered], it stays up through the
 * Mended phase and while an [attempted] apply lands: activation flips to UsedToday the moment the
 * use is recorded, which turns [offered] false however long the write took.
 */
fun isStreakRescueVisible(
    mended: Boolean,
    attempted: Boolean,
    offered: Boolean,
    activation: HealerActivation,
): Boolean = mended || offered || (attempted && activation is HealerActivation.UsedToday)
