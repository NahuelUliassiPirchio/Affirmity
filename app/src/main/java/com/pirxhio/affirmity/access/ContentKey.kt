package com.pirxhio.affirmity.access

/** Extensible without schema migration: a new type is a new enum constant plus its [wireName].
 *  INVARIANT (unit-tested): a [wireName] must never contain `_`, because [ContentKey.storageKey]
 *  splits on the FIRST underscore — content ids do contain underscores (`fuerza_de_voluntad`). */
enum class ContentType(val wireName: String) {
    AFFIRMATION_GROUP("affirmationGroup"),
    MEDITATION("meditation"),
    CUSTOM_AFFIRMATION_SLOT("customAffirmationSlot"),

    /** Collection-level gating for the curated catalog (design D5). The source declares
     *  `access { tier, rewardedUnlockHours }` on COLLECTIONS, never on themes. `wireName` has no
     *  `_` -- the class invariant above is load-bearing here because catalog collection ids are
     *  dotted AND underscored (`self_worth.feeling_enough.intrinsic_worth`). */
    AFFIRMATION_COLLECTION("affirmationCollection");

    companion object {
        fun fromWireName(wireName: String): ContentType? = entries.firstOrNull { it.wireName == wireName }
    }
}

/** Identity of one gateable piece of content. Deliberately NOT a meditation id and NOT a group id —
 *  the persistence layer is keyed by this pair so Spec 3 needs no storage change. */
data class ContentKey(val type: ContentType, val id: String) {

    /** Firestore document id and Room primary key. Alphanumeric + `_` only, so it is a legal
     *  Firestore doc id (no `/`, not `.`/`..`, does not match the reserved `__.*__` pattern). */
    val storageKey: String get() = "${type.wireName}_$id"

    companion object {
        fun parse(storageKey: String): ContentKey? {
            val wireName = storageKey.substringBefore('_', missingDelimiterValue = "")
            val id = storageKey.substringAfter('_', missingDelimiterValue = "")
            val type = ContentType.fromWireName(wireName) ?: return null
            return if (id.isEmpty()) null else ContentKey(type, id)
        }
    }
}
