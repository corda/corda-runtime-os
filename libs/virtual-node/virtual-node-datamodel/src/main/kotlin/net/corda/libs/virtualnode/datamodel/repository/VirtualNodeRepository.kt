package net.corda.libs.virtualnode.datamodel.repository

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.VirtualNodeInfo
import java.util.UUID
import java.util.stream.Stream
import javax.persistence.EntityManager

// using an interface allows us to easily mock/test
interface VirtualNodeRepository {
    fun findAll(entityManager: EntityManager): Stream<VirtualNodeInfo>
    fun find(entityManager: EntityManager, holdingIdentityShortHash: ShortHash): VirtualNodeInfo?

    @Suppress("LongParameterList")

    fun put(
        entityManager: EntityManager,
        holdingId: HoldingIdentity,
        cpiId: CpiIdentifier,
        vaultDDLConnectionId: UUID?,
        vaultDMLConnectionId: UUID,
        cryptoDDLConnectionId: UUID?,
        cryptoDMLConnectionId: UUID,
        uniquenessDDLConnectionId: UUID?,
        uniquenessDMLConnectionId: UUID?
    )
    fun updateVirtualNodeState(entityManager: EntityManager, holdingIdentityShortHash: String, newState: String): VirtualNodeInfo
}

