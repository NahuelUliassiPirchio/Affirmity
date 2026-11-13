package com.pirxhio.affirmity.data.local

/** Runs a block atomically; Room-backed in production, a plain call in JVM tests. */
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

/**
 * Anonymous-generation lifecycle: creating or reusing the active generation at launch, and claiming
 * it for a signed-in uid. Lives outside [AffirmityDatabase] so the transaction bodies run on the JVM.
 */
class ActivityOwnerLifecycle(
    private val owners: ActivityDao,
    private val dailyFacts: ActivityDailyFactDao,
    private val transaction: TransactionRunner,
) {
    suspend fun initialize(candidate: ActivityOwnerEntity): ActivityOwnerEntity = transaction.run {
        val owner = owners.anonymousOwner(candidate)
        dailyFacts.adoptLegacyOnce(owner.owner)
        requireNotNull(owners.owner(owner.owner))
    }

    /**
     * Seals [owner] for [uid] and returns everything it retains. Always seals, even an empty generation:
     * the claim is the write barrier, so a writer that captured the Local session before the swap and runs
     * after it is rejected instead of leaving data in a generation another account could later claim.
     */
    suspend fun claim(owner: String, uid: String): ActivityClaimSnapshot = transaction.run {
        val claimed = owners.claimAnonymous(owner, uid)
        ActivityClaimSnapshot(claimed, dailyFacts.getAll(owner),
            owners.getRange(owner, Long.MIN_VALUE, Long.MAX_VALUE),
            owners.coverageRange(owner, Long.MIN_VALUE, Long.MAX_VALUE))
    }
}
