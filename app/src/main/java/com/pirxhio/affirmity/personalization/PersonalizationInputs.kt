package com.pirxhio.affirmity.personalization

import com.pirxhio.affirmity.data.local.PersonalizationSignalDao
import com.pirxhio.affirmity.data.local.PersonalizationSignalEntity
import com.pirxhio.affirmity.personalization.goals.UserGoalsStore
import com.pirxhio.affirmity.personalization.signal.PersonalizationSignal
import com.pirxhio.affirmity.personalization.signal.SignalType
import kotlinx.coroutines.flow.first

/** Shared one-shot I/O read behind both [loadPersonalizationProfile] and the divergence loader --
 *  a single place for the Room-entity-to-domain-signal mapping so the two features' loaders
 *  cannot silently drift apart. */
internal data class PersonalizationInputs(
    val signals: List<PersonalizationSignal>,
    val declaredGoalIds: Set<String>,
)

internal suspend fun loadPersonalizationInputs(
    signalDao: PersonalizationSignalDao,
    userGoalsStore: UserGoalsStore,
): PersonalizationInputs = PersonalizationInputs(
    signals = signalDao.getAll().map { it.toPersonalizationSignal() },
    declaredGoalIds = userGoalsStore.observeGoalIds().first().orEmpty(),
)

private fun PersonalizationSignalEntity.toPersonalizationSignal() = PersonalizationSignal(
    type = SignalType.valueOf(signalType),
    themeId = themeId,
    groupId = groupId,
    tone = tone,
    occurredAtMillis = occurredAtMillis,
)
