package net.corda.libs.virtualnode.datamodel.repository

import net.corda.crypto.core.ShortHash
import java.time.Instant
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.virtualnode.common.exception.VirtualNodeNotFoundException
import net.corda.libs.virtualnode.common.exception.VirtualNodeOperationNotFoundException
import net.corda.libs.virtualnode.datamodel.entities.HoldingIdentityEntity
import net.corda.libs.virtualnode.datamodel.entities.VirtualNodeEntity
import net.corda.libs.virtualnode.datamodel.dto.VirtualNodeOperationDto
import net.corda.orm.utils.transaction
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.OperationalStatus
import net.corda.virtualnode.VirtualNodeInfo
import java.util.UUID
import java.util.stream.Stream
import javax.persistence.EntityManager
import net.corda.libs.virtualnode.datamodel.dto.VirtualNodeOperationStateDto
import net.corda.libs.virtualnode.datamodel.dto.VirtualNodeOperationType
import net.corda.libs.virtualnode.datamodel.entities.VirtualNodeOperationEntity
import net.corda.libs.virtualnode.datamodel.entities.VirtualNodeOperationState
import net.corda.libs.virtualnode.datamodel.entities.OperationType

class VirtualNodeRepositoryImpl : VirtualNodeRepository {
    /**
     * If you change this function ensure that you check the generated SQL from
     * hibernate in the "findAll test" in
     */
    override fun findAll(entityManager: EntityManager): Stream<VirtualNodeInfo> {
        val query = entityManager.criteriaBuilder!!.createQuery(VirtualNodeEntity::class.java)!!
        val root = query.from(VirtualNodeEntity::class.java)
        root.fetch<Any, Any>("holdingIdentity")
        query.select(root)

        return entityManager.createQuery(query).resultStream.map { it.toVirtualNodeInfo() }
    }

    override fun find(entityManager: EntityManager, holdingIdentityShortHash: ShortHash): VirtualNodeInfo? {
        val queryBuilder = with(entityManager.criteriaBuilder!!) {
            val queryBuilder = createQuery(VirtualNodeEntity::class.java)!!
            val root = queryBuilder.from(VirtualNodeEntity::class.java)
            root.fetch<Any, Any>("holdingIdentity")
            queryBuilder.where(
                equal(
                    root.get<HoldingIdentityEntity>("holdingIdentity").get<String>("holdingIdentityShortHash"),
                    parameter(String::class.java, "shortId")
                )
            ).orderBy(desc(root.get<String>("cpiVersion")))
            queryBuilder
        }

        return entityManager.createQuery(queryBuilder)
            .setParameter("shortId", holdingIdentityShortHash.value.uppercase())
            .setMaxResults(1)
            .resultList
            .singleOrNull()
            ?.toVirtualNodeInfo()
    }

    override fun findVirtualNodeOperationByRequestId(entityManager: EntityManager, requestId: String): List<VirtualNodeOperationDto> {
        entityManager.transaction {
            val operationStatuses = entityManager.createQuery(
                "from ${VirtualNodeOperationEntity::class.java.simpleName} where requestId = :requestId " +
                        "order by latestUpdateTimestamp desc",
                VirtualNodeOperationEntity::class.java
            )
                .setParameter("requestId", requestId)
                .resultList

            if (operationStatuses.isEmpty()) {
                throw VirtualNodeOperationNotFoundException(requestId)
            }

            return operationStatuses.map {
                VirtualNodeOperationDto(
                    it.requestId,
                    it.data,
                    it.operationType.name,
                    it.requestTimestamp,
                    it.latestUpdateTimestamp,
                    it.heartbeatTimestamp,
                    it.state.name,
                    it.errors
                )
            }
        }
    }

    /**
     * Writes a virtual node to the database.
     * @param holdingId Holding identity
     * @param cpiId CPI identifier
     */
    override fun put(
        entityManager: EntityManager,
        holdingId: HoldingIdentity,
        cpiId: CpiIdentifier,
        vaultDDLConnectionId: UUID?,
        vaultDMLConnectionId: UUID,
        cryptoDDLConnectionId: UUID?,
        cryptoDMLConnectionId: UUID,
        uniquenessDDLConnectionId: UUID?,
        uniquenessDMLConnectionId: UUID?
    ) {
        val signerSummaryHash = cpiId.signerSummaryHash.toString()
        val hie = entityManager.find(HoldingIdentityEntity::class.java, holdingId.shortHash.value)
            ?: throw CordaRuntimeException("Could not find holding identity")

        val virtualNodeEntityKey = hie.holdingIdentityShortHash
        val foundVNode = entityManager.find(VirtualNodeEntity::class.java, virtualNodeEntityKey)?.apply {
            this.update(
                vaultDDLConnectionId = vaultDDLConnectionId,
                vaultDMLConnectionId = vaultDMLConnectionId,
                cryptoDDLConnectionId = cryptoDDLConnectionId,
                cryptoDMLConnectionId = cryptoDMLConnectionId,
                uniquenessDDLConnectionId = uniquenessDDLConnectionId,
                uniquenessDMLConnectionId = uniquenessDMLConnectionId
            )
        } ?: VirtualNodeEntity(
            hie.holdingIdentityShortHash,
            hie,
            cpiId.name,
            cpiId.version,
            signerSummaryHash,
            vaultDDLConnectionId = vaultDDLConnectionId,
            vaultDMLConnectionId = vaultDMLConnectionId,
            cryptoDDLConnectionId = cryptoDDLConnectionId,
            cryptoDMLConnectionId = cryptoDMLConnectionId,
            uniquenessDDLConnectionId = uniquenessDDLConnectionId,
            uniquenessDMLConnectionId = uniquenessDMLConnectionId,
        )

        entityManager.persist(foundVNode)
    }

    override fun updateVirtualNodeState(
        entityManager: EntityManager,
        holdingIdentityShortHash: String,
        newState: String
    ): VirtualNodeInfo {
        entityManager.transaction {
            // Lookup virtual node and grab the latest one based on the cpi Version.
            val latestVirtualNodeInstance = findEntity(entityManager, holdingIdentityShortHash)
                ?: throw VirtualNodeNotFoundException(holdingIdentityShortHash)

            var operationalStatus = OperationalStatus.ACTIVE
            if (newState == "maintenance") {
                operationalStatus = OperationalStatus.INACTIVE
            }

            val updatedVirtualNodeInstance = latestVirtualNodeInstance.apply {
                update(
                    operationalStatus,
                    operationalStatus,
                    operationalStatus,
                    operationalStatus
                )
            }
            return it.merge(updatedVirtualNodeInstance).toVirtualNodeInfo()
        }
    }

    override fun upgradeVirtualNodeCpi(
        entityManager: EntityManager,
        holdingIdentityShortHash: String,
        cpiName: String, cpiVersion: String, cpiSignerSummaryHash: String,
        requestId: String, requestTimestamp: Instant, serializedRequest: String
    ): VirtualNodeInfo {
        val virtualNode = entityManager.find(VirtualNodeEntity::class.java, holdingIdentityShortHash)
            ?: throw VirtualNodeNotFoundException(holdingIdentityShortHash)
        virtualNode.cpiName = cpiName
        virtualNode.cpiVersion = cpiVersion
        virtualNode.cpiSignerSummaryHash = cpiSignerSummaryHash
        virtualNode.operationInProgress = VirtualNodeOperationEntity(
            UUID.randomUUID().toString(),
            requestId,
            serializedRequest,
            VirtualNodeOperationState.IN_PROGRESS,
            OperationType.UPGRADE,
            requestTimestamp
        )
        val updatedVirtualNode = entityManager.merge(virtualNode)
        return updatedVirtualNode.toVirtualNodeInfo()
    }

    override fun completedOperation(entityManager: EntityManager, holdingIdentityShortHash: String): VirtualNodeInfo {
        val virtualNode = entityManager.find(VirtualNodeEntity::class.java, holdingIdentityShortHash)
            ?: throw VirtualNodeNotFoundException(holdingIdentityShortHash)

        val operation: VirtualNodeOperationEntity = virtualNode.operationInProgress ?: return virtualNode.toVirtualNodeInfo()

        operation.latestUpdateTimestamp = Instant.now()
        operation.state = VirtualNodeOperationState.COMPLETED
        virtualNode.operationInProgress = null

        entityManager.merge(operation)
        return entityManager.merge(virtualNode)
            .toVirtualNodeInfo()
    }

    override fun rejectedOperation(
        entityManager: EntityManager,
        holdingIdentityShortHash: String,
        requestId: String,
        serializedRequest: String,
        requestTimestamp: Instant,
        reason: String,
        operationType: VirtualNodeOperationType,
        state: VirtualNodeOperationStateDto
    ) {
        entityManager.persist(
            VirtualNodeOperationEntity(
                UUID.randomUUID().toString(),
                requestId,
                serializedRequest,
                VirtualNodeOperationState.fromDto(state),
                OperationType.from(operationType),
                requestTimestamp,
                errors = reason
            )
        )
    }

    override fun failedOperation(
        entityManager: EntityManager,
        holdingIdentityShortHash: String,
        requestId: String,
        serializedRequest: String,
        requestTimestamp: Instant,
        reason: String,
        operationType: VirtualNodeOperationType,
        state: VirtualNodeOperationStateDto
    ) {
        val virtualNode = entityManager.find(VirtualNodeEntity::class.java, holdingIdentityShortHash)
            ?: throw VirtualNodeNotFoundException(holdingIdentityShortHash)

        // Update existing operation that's in progress otherwise create one. Remove operationInProgress as it is now finished with failure
        virtualNode.operationInProgress?.let { operation ->
            operation.latestUpdateTimestamp = Instant.now()
            operation.state = VirtualNodeOperationState.fromDto(state)
            operation.errors = reason
            entityManager.merge(operation)
            virtualNode.operationInProgress = null
            entityManager.merge(virtualNode)
        } ?: run {
            entityManager.persist(
                VirtualNodeOperationEntity(
                    UUID.randomUUID().toString(),
                    requestId,
                    serializedRequest,
                    VirtualNodeOperationState.fromDto(state),
                    OperationType.from(operationType),
                    requestTimestamp,
                    errors = reason
                )
            )
        }
    }

    private fun findEntity(entityManager: EntityManager, holdingIdentityShortHash: String): VirtualNodeEntity? {
        val queryBuilder = with(entityManager.criteriaBuilder!!) {
            val queryBuilder = createQuery(VirtualNodeEntity::class.java)!!
            val root = queryBuilder.from(VirtualNodeEntity::class.java)
            root.fetch<Any, Any>("holdingIdentity")
            queryBuilder.where(
                equal(
                    root.get<HoldingIdentityEntity>("holdingIdentity").get<String>("holdingIdentityShortHash"),
                    parameter(String::class.java, "shortId")
                )
            ).orderBy(desc(root.get<String>("cpiVersion")))
            queryBuilder
        }

        return entityManager.createQuery(queryBuilder)
            .setParameter("shortId", holdingIdentityShortHash.uppercase())
            .setMaxResults(1)
            .resultList
            .singleOrNull()
    }
}