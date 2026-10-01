package com.pirxhio.affirmity.data

/**
 * Domain model of a user collection, as returned by the repository. Kept free of Room types so
 * the pure rules (name validation, limits, feed resolution) can build on it without touching
 * persistence.
 */
data class UserCollection(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val enabled: Boolean,
    val lastUsedAtMillis: Long,
    val affirmationIds: List<String>,
)
