package net.corda.interop.identity.registry.impl

import net.corda.crypto.core.ShortHash
import net.corda.interop.core.InteropIdentity
import net.corda.interop.identity.registry.InteropIdentityRegistryView
import net.corda.v5.application.interop.facade.FacadeId
import java.util.*


class InteropIdentityRegistryViewImpl(private val virtualNodeShortHash: ShortHash): InteropIdentityRegistryView {
    private val interopIdentities = HashSet<InteropIdentity>()

    private val byGroupId: HashMap<UUID, HashSet<InteropIdentity>> = HashMap<UUID, HashSet<InteropIdentity>>()
    private val byVirtualNodeShortHash = HashMap<ShortHash, HashSet<InteropIdentity>>()
    private val byShortHash = HashMap<ShortHash, InteropIdentity>()
    private val myIdentities = HashMap<UUID, InteropIdentity>()
    private val byApplicationName = HashMap<String, InteropIdentity>()
    private val byFacadeId = HashMap<String, HashSet<InteropIdentity>>()

    private fun getOrCreateByGroupIdEntry(groupId: UUID): HashSet<InteropIdentity> {
        return byGroupId.computeIfAbsent(groupId) {
            HashSet()
        }
    }

    private fun getOrCreateByVirtualNodeEntry(shortHash: ShortHash): HashSet<InteropIdentity> {
        return byVirtualNodeShortHash.computeIfAbsent(shortHash) {
            HashSet()
        }
    }

    private fun getOrCreateByFacadeIdEntry(facadeId: FacadeId): HashSet<InteropIdentity> {
        return byFacadeId.computeIfAbsent(facadeId.toString()) {
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
        identity.owningVirtualNodeShortHash.let {
            getOrCreateByVirtualNodeEntry(it).add(identity)
        }

        // Safety check for short hash collisions
        require(byShortHash[identity.shortHash] == null || byShortHash[identity.shortHash] == identity) {
            "Unable to add identity $identity to view of virtual node $virtualNodeShortHash, " +
            "the identity shares a short hash with an existing identity."
        }

        byShortHash[identity.shortHash] = identity
        byApplicationName[identity.applicationName] = identity

        identity.facadeIds.forEach {
            getOrCreateByFacadeIdEntry(it).add(identity)
        }
    }

    fun removeInteropIdentity(identity: InteropIdentity) {
        interopIdentities.remove(identity)
        byGroupId[identity.groupId]?.let {
            it.remove(identity)
            if (it.size == 0) {
                byGroupId.remove(identity.groupId)
            }
        }

        byVirtualNodeShortHash[identity.owningVirtualNodeShortHash]?.let {
            it.remove(identity)
            if (it.size == 0) {
                byVirtualNodeShortHash.remove(identity.owningVirtualNodeShortHash)
            }
        }

        byShortHash[identity.shortHash]?.let {
            byShortHash.remove(identity.shortHash)
        }

        if (identity.owningVirtualNodeShortHash == virtualNodeShortHash) {
            myIdentities.remove(identity.groupId)
        }

        byApplicationName.remove(identity.applicationName)

        byFacadeId.forEach {
            if (it.value.contains(identity)) {
                it.value.remove(identity)
            }
        }
    }

    override fun getIdentities(): Set<InteropIdentity> = interopIdentities

    override fun getIdentitiesByGroupId(): Map<UUID, Set<InteropIdentity>> =
        Collections.unmodifiableMap(byGroupId)

    override fun getIdentitiesByVirtualNode(): Map<ShortHash, Set<InteropIdentity>> =
        Collections.unmodifiableMap(byVirtualNodeShortHash)

    override fun getIdentitiesByShortHash(): Map<ShortHash, InteropIdentity> =
        Collections.unmodifiableMap(byShortHash)

    override fun getIdentitiesByApplicationName(): Map<String, InteropIdentity> =
        Collections.unmodifiableMap(byApplicationName)

    override fun getIdentitiesByFacadeId(): Map<String, Set<InteropIdentity>> =
        Collections.unmodifiableMap(byFacadeId)

    override fun getOwnedIdentities(): Map<UUID, InteropIdentity> =
        Collections.unmodifiableMap(myIdentities)
}
