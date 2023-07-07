package net.corda.interop.identity.cache.impl

import java.util.Collections
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import net.corda.interop.core.InteropIdentity
import net.corda.interop.identity.cache.InteropIdentityCacheView


class InteropIdentityCacheViewImpl(private val holdingIdentityShortHash: String): InteropIdentityCacheView {
    private val interopIdentities = HashSet<InteropIdentity>()

    private val byGroupId = HashMap<String, HashSet<InteropIdentity>>()
    private val byHoldingIdentity = HashMap<String, HashSet<InteropIdentity>>()

    private fun identitiesByGroupId(groupId: String): HashSet<InteropIdentity> {
        return byGroupId.computeIfAbsent(groupId) {
            HashSet()
        }
    }

    private fun identitiesByHoldingIdentity(shortHash: String): HashSet<InteropIdentity> {
        return byHoldingIdentity.computeIfAbsent(shortHash) {
            HashSet()
        }
    }

    fun putInteropIdentity(identity: InteropIdentity) {
        interopIdentities.add(identity)
        identitiesByGroupId(identity.groupId).add(identity)
        identitiesByHoldingIdentity(identity.holdingIdentityShortHash).add(identity)
    }

    fun removeInteropIdentity(identity: InteropIdentity) {
        interopIdentities.remove(identity)
        identitiesByGroupId(identity.groupId).remove(identity)
        identitiesByHoldingIdentity(identity.holdingIdentityShortHash).remove(identity)
    }

    override fun getIdentities(): Set<InteropIdentity> = interopIdentities

    override fun getIdentitiesByGroupId(): Map<String, Set<InteropIdentity>> =
        Collections.unmodifiableMap(byGroupId)

    override fun getIdentitiesByHoldingIdentity(): Map<String, Set<InteropIdentity>> =
        Collections.unmodifiableMap(byHoldingIdentity)
}
