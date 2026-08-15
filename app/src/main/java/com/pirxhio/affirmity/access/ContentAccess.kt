package com.pirxhio.affirmity.access

/** What the USER has and what the CONTENT requires — one enum for both sides, so the core
 *  predicate `userTier satisfies requiredTier` is directly expressible. Replaces
 *  `data.repository.EntitlementTier`. */
enum class AccessTier { FREE, PRO }
