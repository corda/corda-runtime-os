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
import net.corda.utilities.time.Clock
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.write.db.impl.writer.CpiMetadataLite
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbConnections
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbFactory
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeEntityRepository
import net.corda.virtualnode.write.db.impl.writer.management.VirtualNodeManagementHandler
import net.corda.virtualnode.write.db.impl.writer.management.common.MigrationUtility
import net.corda.virtualnode.write.db.impl.writer.management.common.VirtualNodeInfoRecordPublisher
import net.corda.virtualnode.write.db.impl.writer.management.common.impl.VirtualNodeInfoRecordPublisherImpl

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
        instant: Instant,
        request: VirtualNodeCpiUpgradeRequest,
        respFuture: CompletableFuture<VirtualNodeManagementResponse>
    ) {
        try {
            request.validateFields()
            val upgradeCpiMetadata = validateUpgradeCpi(request.cpiFileChecksum)

            val (holdingId, connections) = getHoldingIdentity(request.virtualNodeShortId)
            val currentVirtualNode = findCurrentVirtualNodeByHoldingId(holdingId)
            validateCpiInSameGroup(currentVirtualNode, upgradeCpiMetadata)

            val (vaultDDLConnectionConfig, vaultDMLConnectionConfig) =
                getVaultSchemaConnectionConfigs(connections)

            updateVirtualNodeCpi(holdingId, upgradeCpiMetadata.id)

            runCpiMigrations(holdingId, upgradeCpiMetadata, vaultDDLConnectionConfig?.config, vaultDMLConnectionConfig?.config)

            publishNewlyUpgradedVirtualNodeInfo(holdingId, upgradeCpiMetadata, connections)

            // update mgm mapping for virtual node

            // switch off maintenance mode for vnode

            // run smoke tests in the CPKs
        } catch (e: Exception) {
            handleException(respFuture, e)
        }
    }

    private fun findCurrentVirtualNodeByHoldingId(holdingIdentity: HoldingIdentity): VirtualNodeEntityRepository.VirtualNodeLite {
        return virtualNodeEntityRepository.findByHoldingIdentity(holdingIdentity)
    }

    private fun publishNewlyUpgradedVirtualNodeInfo(holdingIdentity: HoldingIdentity, cpiMetadata: CpiMetadataLite, dbConnections: VirtualNodeDbConnections) {
        virtualNodeInfoPublisher.publishVNodeInfo(holdingIdentity, cpiMetadata, dbConnections)
    }

    private fun getVaultSchemaConnectionConfigs(
        vnodeDbConnections: VirtualNodeDbConnections
    ): Pair<DbConnectionLite?, DbConnectionLite?> {
        val vaultDDLConnection = vnodeDbConnections.vaultDdlConnectionId?.let {
            dbConnectionsRepository.get(it)
        }
        val vaultDMLConnection = vnodeDbConnections.vaultDmlConnectionId?.let {
            dbConnectionsRepository.get(it)
        }
        return Pair(vaultDDLConnection, vaultDMLConnection)
    }

    private fun getHoldingIdentity(holdingIdentityShortHash: String): VirtualNodeEntityRepository.HoldingIdentityAndConnections {
        return virtualNodeEntityRepository.getHoldingIdentityAndConnections(ShortHash.Companion.of(holdingIdentityShortHash))
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

    private fun validateUpgradeCpi(cpiFileChecksum: String): CpiMetadataLite {
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