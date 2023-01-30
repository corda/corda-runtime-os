package net.corda.libs.virtualnode.datamodel.repository

import java.time.Instant
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.VirtualNodeInfo
import java.util.UUID
import java.util.stream.Stream
import javax.persistence.EntityManager

/**
 * Interface for CRUD operations for a virtual node.
 */
interface VirtualNodeRepository {
    /**
     * Find all virtual nodes.
     */
    fun findAll(entityManager: EntityManager): Stream<VirtualNodeInfo>

    /**
     * Find a virtual node identified by the given holdingIdentity short hash
     */
    fun find(entityManager: EntityManager, holdingIdentityShortHash: ShortHash): VirtualNodeInfo?

    /**
     * Persist a holding identity with the given holdingId and CPI.
     */
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

    /**
     * Update a virtual node's state.
     */
    fun updateVirtualNodeState(entityManager: EntityManager, holdingIdentityShortHash: String, newState: String): VirtualNodeInfo

    /**
     * Upgrade the CPI associated with a virtual node.
     */
    fun upgradeVirtualNodeCpi(
        entityManager: EntityManager,
        holdingIdentityShortHash: String,
        cpiName: String, cpiVersion: String, cpiSignerSummaryHash: String,
        requestId: String, requestTimestamp: Instant, serializedRequest: String
    ): VirtualNodeInfo

    /**
     * Complete an in-progress operation on a virtual node.
     */
    fun completeOperation(
        entityManager: EntityManager,
        holdingIdentityShortHash: String
    ): VirtualNodeInfo
}

