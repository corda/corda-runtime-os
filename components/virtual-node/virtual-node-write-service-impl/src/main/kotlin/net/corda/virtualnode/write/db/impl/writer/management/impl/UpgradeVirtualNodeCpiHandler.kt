package net.corda.virtualnode.write.db.impl.writer.management.impl

import java.time.Instant
import java.util.concurrent.CompletableFuture
import net.corda.data.ExceptionEnvelope
import net.corda.data.virtualnode.VirtualNodeCpiUpgradeRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.data.virtualnode.VirtualNodeManagementResponseFailure
import net.corda.db.connection.manager.DbConnectionLite
import net.corda.db.connection.manager.DbConnectionsRepository
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.virtualnode.common.exception.CpiNotFoundException
import net.corda.libs.virtualnode.common.exception.HoldingIdentityNotFoundException
import net.corda.libs.virtualnode.common.exception.MgmGroupMismatchException
import net.corda.libs.virtualnode.common.exception.VirtualNodeNotFoundException
import net.corda.utilities.time.Clock
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.write.db.impl.writer.CpiMetadataLite
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbConnections
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbFactory
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeEntityRepository
import net.corda.virtualnode.write.db.impl.writer.management.VirtualNodeManagementHandler
import net.corda.virtualnode.write.db.impl.writer.management.common.MigrationUtility
import net.corda.virtualnode.write.db.impl.writer.management.common.VirtualNodeInfoRecordPublisher

internal class UpgradeVirtualNodeCpiHandler(
    private val virtualNodeEntityRepository: VirtualNodeEntityRepository,
    private val dbConnectionsRepository: DbConnectionsRepository,
    private val vnodeDbFactory: VirtualNodeDbFactory,
    private val migrationUtility: MigrationUtility,
    private val virtualNodeInfoPublisher: VirtualNodeInfoRecordPublisher,
    private val clock: Clock,
) : VirtualNodeManagementHandler<VirtualNodeCpiUpgradeRequest> {

    private companion object {
        val logger = contextLogger()
        const val PUBLICATION_TIMEOUT_SECONDS = 30L
    }

    override fun handle(
        requestTimestamp: Instant,
        request: VirtualNodeCpiUpgradeRequest,
        respFuture: CompletableFuture<VirtualNodeManagementResponse>
    ) {
        upgradeVirtualNodeCpi(requestTimestamp, request, respFuture)
    }

    private fun upgradeVirtualNodeCpi(
        requestTimestamp: Instant,
        request: VirtualNodeCpiUpgradeRequest,
        respFuture: CompletableFuture<VirtualNodeManagementResponse>
    ) {
        try {
            request.validateFields()

            // todo work out which of these operations can be condensed into one transaction

            val currentVirtualNode = findCurrentVirtualNode(request.virtualNodeShortId)
            validateCurrentVirtualNodeMaintenance(currentVirtualNode)

            val upgradeCpiMetadata = findUpgradeCpi(request.cpiFileChecksum)
            val (holdingId, connections) = findHoldingIdentityAndConnections(request.virtualNodeShortId)

            val originalCpiMetadata = findCurrentCpiMetadata(currentVirtualNode.cpiName, currentVirtualNode.cpiVersion)

            validateCpiInSameGroup(originalCpiMetadata, upgradeCpiMetadata)

            val (vaultDDLConnectionConfig, vaultDMLConnectionConfig) =
                getVaultSchemaConnectionConfigs(connections)

            updateVirtualNodeCpi(holdingId, upgradeCpiMetadata.id)
            try {
                runCpiMigrations(holdingId, upgradeCpiMetadata, vaultDDLConnectionConfig?.config, vaultDMLConnectionConfig?.config)
            } catch (e : Exception) {
                rollBackVirtualNodeToOriginalCpi(holdingId, originalCpiMetadata)
                throw e
            }

            publishNewlyUpgradedVirtualNodeInfo(holdingId, upgradeCpiMetadata, connections)

            updateVirtualNodeToActive(holdingId.shortHash.value)

            // update mgm mapping for virtual node
            publishMgmReRegistration()

        } catch (e: Exception) {
            handleException(respFuture, e)
        }
    }

    private fun publishMgmReRegistration() {
        TODO("Not yet implemented")
    }

    private fun updateVirtualNodeToActive(holdingIdShortHash: String) {
        virtualNodeEntityRepository.setVirtualNodeState(holdingIdShortHash, "ACTIVE")
    }

    private fun rollBackVirtualNodeToOriginalCpi(holdingId: HoldingIdentity, currentCpiMetadata: CpiMetadataLite) {
        updateVirtualNodeCpi(holdingId, currentCpiMetadata.id)
    }

    private fun validateCurrentVirtualNodeMaintenance(currentVirtualNode: VirtualNodeEntityRepository.VirtualNodeLite) {
        require(currentVirtualNode.virtualNodeState == "IN_MAINTENANCE") {
            "Virtual nodes must be in maintenance mode to upgrade a CPI. " +
                    "Virtual node '${currentVirtualNode.holdingIdentityShortHash}' state was '${currentVirtualNode.virtualNodeState}'"
        }
    }

    private fun findCurrentCpiMetadata(cpiName: String, cpiVersion: String): CpiMetadataLite {
        return requireNotNull(virtualNodeEntityRepository.getCPIMetadataByNameAndVersion(cpiName, cpiVersion)) {
            "CPI with name $cpiName, version $cpiVersion was not found."
        }
    }

    private fun validateCpiInSameGroup(
        currentCpiMetadata: CpiMetadataLite,
        upgradeCpiMetadata: CpiMetadataLite
    ) {
        if (currentCpiMetadata.mgmGroupId != upgradeCpiMetadata.mgmGroupId) {
            throw MgmGroupMismatchException(currentCpiMetadata.mgmGroupId, upgradeCpiMetadata.mgmGroupId)
        }
    }

    private fun findCurrentVirtualNode(holdingIdentityShortHash: String): VirtualNodeEntityRepository.VirtualNodeLite {
        return virtualNodeEntityRepository.findByHoldingIdentity(holdingIdentityShortHash)
            ?: throw VirtualNodeNotFoundException(holdingIdentityShortHash)
    }

    private fun publishNewlyUpgradedVirtualNodeInfo(
        holdingIdentity: HoldingIdentity,
        cpiMetadata: CpiMetadataLite,
        dbConnections: VirtualNodeDbConnections
    ) {
        virtualNodeInfoPublisher.publishVNodeInfo(holdingIdentity, cpiMetadata, dbConnections)
    }

    private fun getVaultSchemaConnectionConfigs(
        vnodeDbConnections: VirtualNodeDbConnections
    ): Pair<DbConnectionLite?, DbConnectionLite?> {
        val vaultDDLConnection = vnodeDbConnections.vaultDdlConnectionId?.let {
            dbConnectionsRepository.get(it)
        }
        val vaultDMLConnection = vnodeDbConnections.vaultDmlConnectionId.let {
            dbConnectionsRepository.get(it)
        }
        return Pair(vaultDDLConnection, vaultDMLConnection)
    }

    private fun findHoldingIdentityAndConnections(holdingIdentityShortHash: String): VirtualNodeEntityRepository.HoldingIdentityAndConnections {
        return virtualNodeEntityRepository.getHoldingIdentityAndConnections(holdingIdentityShortHash)
            ?: throw HoldingIdentityNotFoundException(holdingIdentityShortHash)
    }

    private fun runCpiMigrations(
        holdingId: HoldingIdentity,
        cpiMetadata: CpiMetadataLite,
        vaultDDLConnectionConfig: String?,
        vaultDMLConnectionConfig: String?
    ) {
        val vaultDb = vnodeDbFactory.createVaultDbs(
            holdingId.shortHash,
            vaultDDLConnectionConfig,
            vaultDMLConnectionConfig
        )
        migrationUtility.runCpiMigrations(cpiMetadata, vaultDb)
    }

    private fun updateVirtualNodeCpi(holdingIdentity: HoldingIdentity, cpiId: CpiIdentifier) {
        virtualNodeEntityRepository.updateVirtualNodeCpi(holdingIdentity, cpiId)
    }

    private fun findUpgradeCpi(cpiFileChecksum: String): CpiMetadataLite {
        return virtualNodeEntityRepository.getCpiMetadataByChecksum(cpiFileChecksum)
            ?: throw CpiNotFoundException("CPI with file checksum $cpiFileChecksum was not found.")
    }

    // todo duplicate of VirtualNodeWriterProcessor
    private fun handleException(respFuture: CompletableFuture<VirtualNodeManagementResponse>, e: Exception) {
        logger.error("Error while processing virtual node request: ${e.message}", e)
        val response = VirtualNodeManagementResponse(
            clock.instant(),
            VirtualNodeManagementResponseFailure(
                ExceptionEnvelope().apply {
                    errorType = e::class.java.name
                    errorMessage = e.message
                }
            )
        )
        respFuture.complete(response)
    }

    private fun VirtualNodeCpiUpgradeRequest.validateFields() {
        requireNotNull(virtualNodeShortId) {
            "Virtual node identifier is missing"
        }
        requireNotNull(cpiFileChecksum) {
            "CPI file checksum is missing"
        }
    }
}