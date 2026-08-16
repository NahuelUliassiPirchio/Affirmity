package com.pirxhio.affirmity.access

/**
 * The ONLY way an ad unlock is ever created. [resolveAccess] never creates one, so "opt-in only"
 * is structural. Spec 1 ships this interface and [NoAdUnlockSource] and adds NO ad SDK to
 * `gradle/libs.versions.toml`; Spec 5 supplies a rewarded-ad implementation and changes nothing
 * else in this spec's code (design §9).
 */
interface AdUnlockSource {
    suspend fun requestUnlock(key: ContentKey, policy: AdUnlockPolicy): AdUnlockOutcome
}

sealed interface AdUnlockOutcome {
    /** The ONLY outcome that grants. */
    data object Earned : AdUnlockOutcome

    /** User backed out. */
    data object Dismissed : AdUnlockOutcome

    /** No fill / SDK error. */
    data class Failed(val reason: String) : AdUnlockOutcome

    /** No source wired (Spec 1 default). */
    data object Unavailable : AdUnlockOutcome
}

/** Default wiring until Spec 5. Never grants -- proves the whole path is correct with zero ad SDK
 *  in the build. */
object NoAdUnlockSource : AdUnlockSource {
    override suspend fun requestUnlock(key: ContentKey, policy: AdUnlockPolicy): AdUnlockOutcome =
        AdUnlockOutcome.Unavailable
}
