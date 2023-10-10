package net.corda.interop.identity.registry.impl

import net.corda.crypto.core.ShortHash
import net.corda.interop.core.InteropIdentity
import net.corda.interop.identity.registry.InteropIdentityRegistryView
import net.corda.v5.application.interop.facade.FacadeId
import java.util.*
import net.corda.interop.identity.registry.InteropIdentityRegistryStateError
import org.slf4j.Logger
import org.slf4j.LoggerFactory


@Suppress("TooManyFunctions")
class InteropIdentityRegistryViewImpl(private val virtualNodeShortHash: ShortHash): InteropIdentityRegistryView {
    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val interopIdentities = InteropIdentityRegistrySet()

    private val ownedIdentities = HashMap<UUID, InteropIdentityRegistrySet>()
    private val byGroupId = HashMap<UUID, InteropIdentityRegistrySet>()
    private val byVirtualNodeShortHash = HashMap<ShortHash, InteropIdentityRegistrySet>()
    private val byApplicationName = HashMap<String, InteropIdentityRegistrySet>()
    private val byFacadeId = HashMap<FacadeId, InteropIdentityRegistrySet>()


    private fun getOrCreateOwnedIdentitiesEntry(groupId: UUID): InteropIdentityRegistrySet {
        return ownedIdentities.computeIfAbsent(groupId) {
            InteropIdentityRegistrySet()
        }
    }

    private fun getOrCreateByGroupIdEntry(groupId: UUID): InteropIdentityRegistrySet {
        return byGroupId.computeIfAbsent(groupId) {
            InteropIdentityRegistrySet()
        }
    }

    private fun getOrCreateByVirtualNodeEntry(shortHash: ShortHash): InteropIdentityRegistrySet {
        return byVirtualNodeShortHash.computeIfAbsent(shortHash) {
            InteropIdentityRegistrySet()
        }
    }

    private fun getOrCreateByApplicationNameEntry(applicationName: String): InteropIdentityRegistrySet {
        return byApplicationName.computeIfAbsent(applicationName) {
            InteropIdentityRegistrySet()
        }
    }

    private fun getOrCreateByFacadeIdEntry(facadeId: FacadeId): InteropIdentityRegistrySet {
        return byFacadeId.computeIfAbsent(facadeId) {
            InteropIdentityRegistrySet()
        }
    }

    fun putInteropIdentity(identity: InteropIdentity) {
        interopIdentities.add(identity)

        if (identity.owningVirtualNodeShortHash == virtualNodeShortHash) {
            getOrCreateOwnedIdentitiesEntry(identity.groupId).add(identity)
        }

        getOrCreateByGroupIdEntry(identity.groupId).add(identity)
        getOrCreateByVirtualNodeEntry(identity.owningVirtualNodeShortHash).add(identity)
        getOrCreateByApplicationNameEntry(identity.applicationName).add(identity)

        identity.facadeIds.forEach {
            getOrCreateByFacadeIdEntry(it).add(identity)
        }
    }

    fun removeInteropIdentity(identity: InteropIdentity) {
        val actualIdentity = interopIdentities.get(identity.shortHash) ?: return

        if (actualIdentity != identity) {
            log.warn(
                "Removing interop identity from registry view of node '$virtualNodeShortHash', but " +
                "the identity to remove does not match the existing identity."
            )
        }

        if (actualIdentity.owningVirtualNodeShortHash == virtualNodeShortHash) {
            ownedIdentities[actualIdentity.groupId]?.remove(actualIdentity)
        }

        byGroupId[actualIdentity.groupId]?.remove(actualIdentity)
        byVirtualNodeShortHash[actualIdentity.owningVirtualNodeShortHash]?.remove(actualIdentity)
        byApplicationName[actualIdentity.applicationName]?.remove(actualIdentity)

        actualIdentity.facadeIds.forEach { facadeId ->
            byFacadeId[facadeId]?.remove(actualIdentity)
        }

        interopIdentities.remove(actualIdentity)
    }

    override fun getIdentities(): Set<InteropIdentity> =
        Collections.unmodifiableSet(interopIdentities.toSet())

    override fun getIdentitiesByGroupId(groupId: UUID): Set<InteropIdentity> =
        Collections.unmodifiableSet(byGroupId[groupId]?.toSet() ?: emptySet())

    override fun getIdentityWithShortHash(shortHash: ShortHash): InteropIdentity? =
        interopIdentities.get(shortHash)

    override fun getIdentitiesByApplicationName(applicationName: String): Set<InteropIdentity> =
        Collections.unmodifiableSet(byApplicationName[applicationName]?.toSet() ?: emptySet())

    override fun getIdentityWithApplicationName(applicationName: String): InteropIdentity? {
        val identities = getIdentitiesByApplicationName(applicationName)
        if (identities.size > 1) {
            throw InteropIdentityRegistryStateError(
                "Registry view of virtual node $virtualNodeShortHash contains multiple interop identities with " +
                        "application name $applicationName."
            )
        }
        return identities.singleOrNull()
    }

    override fun getIdentitiesByFacadeId(facadeId: FacadeId): Set<InteropIdentity> =
        Collections.unmodifiableSet(byFacadeId[facadeId]?.toSet() ?: emptySet())

    override fun getOwnedIdentities(groupId: UUID): Set<InteropIdentity> =
        Collections.unmodifiableSet(ownedIdentities[groupId]?.toSet() ?: emptySet())

    override fun getOwnedIdentity(groupId: UUID): InteropIdentity? {
        val ownedIdentities = getOwnedIdentities(groupId)
        if (ownedIdentities.size > 1) {
            throw InteropIdentityRegistryStateError(
                "Registry view of virtual node $virtualNodeShortHash contains multiple owned interop identities " +
                        "within interop group $groupId."
            )
        }
        return ownedIdentities.singleOrNull()
    }
}
