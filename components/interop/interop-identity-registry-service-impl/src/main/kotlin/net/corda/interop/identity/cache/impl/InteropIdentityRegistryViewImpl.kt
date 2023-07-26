package net.corda.interop.identity.cache.impl

import java.util.Collections
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import net.corda.interop.core.InteropIdentity
import net.corda.interop.identity.cache.InteropIdentityRegistryView


class InteropIdentityRegistryViewImpl(private val virtualNodeShortHash: String): InteropIdentityRegistryView {
    private val interopIdentities = HashSet<InteropIdentity>()

    private val byGroupId = HashMap<String, HashSet<InteropIdentity>>()
    private val byVirtualNode = HashMap<String, HashSet<InteropIdentity>>()
    private val byShortHash = HashMap<String, InteropIdentity>()
    private val myIdentities = HashMap<String, InteropIdentity>()

    private fun getOrCreateByGroupIdEntry(groupId: String): HashSet<InteropIdentity> {
        return byGroupId.computeIfAbsent(groupId) {
            HashSet()
        }
    }

    private fun getOrCreateByVirtualNodeEntry(shortHash: String): HashSet<InteropIdentity> {
        return byVirtualNode.computeIfAbsent(shortHash) {
            HashSet()
        }
    }

    fun putInteropIdentity(identity: InteropIdentity) {
        if (identity.owningVirtualNodeShortHash == virtualNodeShortHash) {
            val existingOwnedIdentity = myIdentities[identity.groupId]
            require(existingOwnedIdentity == null || identity == existingOwnedIdentity) {
                "Unable to add identity $identity to view of virtual node $virtualNodeShortHash, " +
                "specified virtual node already owns an identity in this interop group."
            }
            myIdentities[identity.groupId] = identity
        }

        interopIdentities.add(identity)

        getOrCreateByGroupIdEntry(identity.groupId).add(identity)
        getOrCreateByVirtualNodeEntry(identity.owningVirtualNodeShortHash).add(identity)

        // Safety check for short hash collisions
        require(byShortHash[identity.shortHash] == null || byShortHash[identity.shortHash] == identity) {
            "Unable to add identity $identity to view of virtual node $virtualNodeShortHash, " +
            "the identity shares a short hash with an existing identity."
        }

        byShortHash[identity.shortHash] = identity
    }

    fun removeInteropIdentity(identity: InteropIdentity) {
        interopIdentities.remove(identity)

        byGroupId[identity.groupId]?.let {
            it.remove(identity)
            if (it.size == 0) {
                byGroupId.remove(identity.groupId)
            }
        }

        byVirtualNode[identity.owningVirtualNodeShortHash]?.let {
            it.remove(identity)
            if (it.size == 0) {
                byGroupId.remove(identity.owningVirtualNodeShortHash)
            }
        }

        byShortHash[identity.shortHash]?.let {
            byShortHash.remove(identity.shortHash)
        }

        if (identity.owningVirtualNodeShortHash == virtualNodeShortHash) {
            myIdentities.remove(identity.groupId)
        }
    }

    override fun getIdentities(): Set<InteropIdentity> = interopIdentities

    override fun getIdentitiesByGroupId(): Map<String, Set<InteropIdentity>> =
        Collections.unmodifiableMap(byGroupId)

    override fun getIdentitiesByVirtualNode(): Map<String, Set<InteropIdentity>> =
        Collections.unmodifiableMap(byVirtualNode)

    override fun getIdentitiesByShortHash(): Map<String, InteropIdentity> =
        Collections.unmodifiableMap(byShortHash)

    override fun getOwnedIdentities(): Map<String, InteropIdentity> =
        Collections.unmodifiableMap(myIdentities)
}
