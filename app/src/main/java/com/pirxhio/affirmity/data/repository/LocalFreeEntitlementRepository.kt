package com.pirxhio.affirmity.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * [EntitlementRepository] for a signed-out session (design.md D5, spec's "Signed-out session is
 * always Free"). A signed-out user has no Firestore doc to read from, so "Free" is a type-level
 * property here, not a runtime check.
 */
class LocalFreeEntitlementRepository : EntitlementRepository {
    override fun observe(): Flow<Entitlement> = flowOf(Entitlement.Free)
}
