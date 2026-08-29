package com.pirxhio.affirmity.access

/** A DURABLE ad unlock (i.e. [AdUnlockPolicy.ONE_TIME_TRIAL] only). PER_USE never produces one of
 *  these. [expiresAtMillis] is `null` for a permanent grant; the field exists so a future
 *  time-boxed trial needs no storage migration (Firestore is schemaless, Room's column is
 *  nullable from day one). */
data class AdUnlockRecord(
    val key: ContentKey,
    val grantedAtMillis: Long,
    val expiresAtMillis: Long? = null,
) {
    fun hasExpired(nowMillis: Long): Boolean =
        expiresAtMillis != null && nowMillis >= expiresAtMillis
}

/** The complete grant state fed to [resolveAccess]. Two fields because the two policies have
 *  genuinely different durability (design §0) and collapsing them would force one of the two into
 *  the wrong lifetime. */
data class AdUnlockState(
    /** PER_USE grants earned in THIS process for THIS identity. Lives in `AffirmityAppState`,
     *  never in Room or Firestore. */
    val sessionUnlocks: Set<ContentKey> = emptySet(),

    /** ONE_TIME_TRIAL grants, mirrored from `AdUnlockRepository.observeDurableUnlocks()`. */
    val durableUnlocks: Map<ContentKey, AdUnlockRecord> = emptyMap(),

    /** TIMED_REPEATABLE grants, mirrored from `AdUnlockRepository.observeTimedUnlocks()`.
     *  Separate from [durableUnlocks] because its store permits overwrite and [durableUnlocks]'
     *  store must never (design D16). */
    val timedUnlocks: Map<ContentKey, AdUnlockRecord> = emptyMap(),
)
