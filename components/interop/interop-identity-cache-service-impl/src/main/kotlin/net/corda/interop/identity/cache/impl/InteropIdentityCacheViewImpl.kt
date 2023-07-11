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
    private val byShortHash = HashMap<String, HashSet<InteropIdentity>>()
    private val myIdentities = HashMap<String, InteropIdentity>()

    private fun getOrCreateByGroupIdEntry(groupId: String): HashSet<InteropIdentity> {
        return byGroupId.computeIfAbsent(groupId) {
            HashSet()
        }
    }

    private fun getOrCreateByHoldingIdentityEntry(shortHash: String): HashSet<InteropIdentity> {
        return byHoldingIdentity.computeIfAbsent(shortHash) {
            HashSet()
        }
    }

    private fun getOrCreateByShortHashIdentity(shortHash: String): HashSet<InteropIdentity> {
        return byShortHash.computeIfAbsent(shortHash) {
            HashSet()
        }
    }

    fun putInteropIdentity(identity: InteropIdentity) {
        if (identity.holdingIdentityShortHash == holdingIdentityShortHash) {
            val existingOwnedIdentity = myIdentities[identity.groupId]
            require(existingOwnedIdentity == null || identity == existingOwnedIdentity) {
                "Unable to add identity $identity to context of holding identity $holdingIdentityShortHash, " +
                "specified holding identity already owns an identity in this interop group."
            }
            myIdentities[identity.groupId] = identity
        }

        interopIdentities.add(identity)

        getOrCreateByGroupIdEntry(identity.groupId).add(identity)
        getOrCreateByHoldingIdentityEntry(identity.holdingIdentityShortHash).add(identity)
        getOrCreateByShortHashIdentity(identity.shortHash).add(identity)
    }

    fun removeInteropIdentity(identity: InteropIdentity) {
        interopIdentities.remove(identity)

        byGroupId[identity.groupId]?.let {
            it.remove(identity)
            if (it.size == 0) {
                byGroupId.remove(identity.groupId)
            }
        }

        byHoldingIdentity[identity.holdingIdentityShortHash]?.let {
            it.remove(identity)
            if (it.size == 0) {
                byGroupId.remove(identity.holdingIdentityShortHash)
            }
        }

        byShortHash[identity.shortHash]?.let {
            it.remove(identity)
            if (it.size == 0) {
                byShortHash.remove(identity.shortHash)
            }
        }

        if (identity.holdingIdentityShortHash == holdingIdentityShortHash) {
            myIdentities.remove(identity.groupId)
        }
    }

    override fun getIdentities(): Set<InteropIdentity> = interopIdentities

    override fun getIdentitiesByGroupId(): Map<String, Set<InteropIdentity>> =
        Collections.unmodifiableMap(byGroupId)

    override fun getIdentitiesByHoldingIdentity(): Map<String, Set<InteropIdentity>> =
        Collections.unmodifiableMap(byHoldingIdentity)

    override fun getIdentitiesByShortHash(): Map<String, Set<InteropIdentity>> =
        Collections.unmodifiableMap(byShortHash)

    override fun getOwnedIdentities(): Map<String, InteropIdentity> =
        Collections.unmodifiableMap(myIdentities)
}
