package net.corda.interop.identity.cache

import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet


/**
 * This class represents the state of the interop configuration visible to a specific virtual node.
 */
class InteropIdentityCacheView(private val holdingIdentityShortHash: String) {
    private val interopIdentities = HashSet<InteropIdentityCacheEntry>()

    private val byGroupId = HashMap<String, HashSet<InteropIdentityCacheEntry>>()
    private val byHoldingIdentity = HashMap<String, HashSet<InteropIdentityCacheEntry>>()

    private fun identitiesByGroupId(groupId: String): HashSet<InteropIdentityCacheEntry> {
        return byGroupId.computeIfAbsent(groupId) {
            HashSet()
        }
    }


    private fun identitiesByHoldingIdentity(shortHash: String): HashSet<InteropIdentityCacheEntry> {
        return byHoldingIdentity.computeIfAbsent(shortHash) {
            HashSet()
        }
    }


    fun addIdentity(identity: InteropIdentityCacheEntry) {
        interopIdentities.add(identity)
        identitiesByGroupId(identity.groupId).add(identity)
        identitiesByHoldingIdentity(identity.holdingIdentityShortHash).add(identity)
    }


    fun removeIdentity(identity: InteropIdentityCacheEntry) {
        interopIdentities.remove(identity)
        identitiesByGroupId(identity.groupId).remove(identity)
        identitiesByHoldingIdentity(identity.holdingIdentityShortHash).remove(identity)
    }


    /** Separate public methods to return immutable containers. */
    fun getIdentities(): Set<InteropIdentityCacheEntry> = interopIdentities
    fun getIdentitiesByGroupId(): Map<String, Set<InteropIdentityCacheEntry>> =
        Collections.unmodifiableMap(byGroupId)
    fun getIdentitiesByHoldingIdentity(): Map<String, Set<InteropIdentityCacheEntry>> =
        Collections.unmodifiableMap(byHoldingIdentity)
}
