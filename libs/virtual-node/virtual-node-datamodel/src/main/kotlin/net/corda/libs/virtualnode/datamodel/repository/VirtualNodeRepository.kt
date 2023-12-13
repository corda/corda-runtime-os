package net.corda.libs.virtualnode.datamodel.repository

import net.corda.crypto.core.ShortHash
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.virtualnode.common.exception.VirtualNodeOperationNotFoundException
import net.corda.libs.virtualnode.datamodel.dto.VirtualNodeOperationDto
import net.corda.libs.virtualnode.datamodel.dto.VirtualNodeOperationStateDto
import net.corda.libs.virtualnode.datamodel.dto.VirtualNodeOperationType
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.OperationalStatus
import net.corda.virtualnode.VirtualNodeInfo
import java.time.Instant
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
     * Find a virtual node operation by the given operation requestId
     * @throws VirtualNodeOperationNotFoundException
     */
    fun findVirtualNodeOperationByRequestId(entityManager: EntityManager, requestId: String): List<VirtualNodeOperationDto>

    /**
     * Stores virtual node operation into the DB
     */
    fun putVirtualNodeOperation(entityManager: EntityManager, operation: VirtualNodeOperationDto)

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
        uniquenessDMLConnectionId: UUID?,
        externalMessagingRouteConfig: String?
    )

    /**
     * Update a virtual node's state.
     */
    fun updateVirtualNodeState(entityManager: EntityManager, holdingIdentityShortHash: String, newState: OperationalStatus): VirtualNodeInfo

    /**
     * Upgrade the CPI associated with a virtual node.
     */
    @Suppress("LongParameterList")
    fun upgradeVirtualNodeCpi(
        entityManager: EntityManager,
        holdingIdentityShortHash: String,
        cpiName: String,
        cpiVersion: String,
        cpiSignerSummaryHash: String,
        externalMessagingRouteConfig: String?,
        requestId: String,
        requestTimestamp: Instant,
        serializedRequest: String
    ): VirtualNodeInfo

    /**
     * Complete an in-progress operation on a virtual node.
     */
    fun completedOperation(
        entityManager: EntityManager,
        holdingIdentityShortHash: String
    ): VirtualNodeInfo

    /**
     * Given a virtual node identified by the [holdingIdentityShortHash], remove any operation in progress associated with this virtual
     * node and update the operation record with failure details.
     */
    @Suppress("LongParameterList")
    fun failedOperation(
        entityManager: EntityManager,
        holdingIdentityShortHash: String,
        requestId: String,
        serializedRequest: String,
        requestTimestamp: Instant,
        reason: String,
        operationType: VirtualNodeOperationType,
        state: VirtualNodeOperationStateDto
    ): VirtualNodeInfo
}
