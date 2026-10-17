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
