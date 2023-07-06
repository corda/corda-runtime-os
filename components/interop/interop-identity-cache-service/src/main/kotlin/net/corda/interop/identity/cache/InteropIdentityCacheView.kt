package net.corda.interop.identity.cache

import java.util.Collections
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import net.corda.interop.core.InteropIdentity


/**
 * This class represents the state of the interop configuration visible to a specific virtual node.
 */
class InteropIdentityCacheView(private val holdingIdentityShortHash: String) {
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


    fun addIdentity(identity: InteropIdentity) {
        interopIdentities.add(identity)
        identitiesByGroupId(identity.groupId).add(identity)
        identitiesByHoldingIdentity(identity.holdingIdentityShortHash).add(identity)
    }


    fun removeIdentity(identity: InteropIdentity) {
        interopIdentities.remove(identity)
        identitiesByGroupId(identity.groupId).remove(identity)
        identitiesByHoldingIdentity(identity.holdingIdentityShortHash).remove(identity)
    }


    /** Separate public methods to return immutable containers. */
    fun getIdentities(): Set<InteropIdentity> = interopIdentities
    fun getIdentitiesByGroupId(): Map<String, Set<InteropIdentity>> =
        Collections.unmodifiableMap(byGroupId)
    fun getIdentitiesByHoldingIdentity(): Map<String, Set<InteropIdentity>> =
        Collections.unmodifiableMap(byHoldingIdentity)
}
