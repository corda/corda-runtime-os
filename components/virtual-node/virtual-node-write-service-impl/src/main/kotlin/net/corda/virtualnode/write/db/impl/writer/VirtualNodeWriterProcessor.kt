package net.corda.virtualnode.write.db.impl.writer

import net.corda.crypto.core.ShortHash
import net.corda.data.ExceptionEnvelope
import net.corda.data.virtualnode.VirtualNodeDBResetRequest
import net.corda.data.virtualnode.VirtualNodeDBResetResponse
import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.data.virtualnode.VirtualNodeManagementResponseFailure
import net.corda.data.virtualnode.VirtualNodeOperationStatusRequest
import net.corda.data.virtualnode.VirtualNodeStateChangeRequest
import net.corda.data.virtualnode.VirtualNodeStateChangeResponse
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.VirtualNodeDbType.VAULT
import net.corda.db.core.CloseableDataSource
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.cpi.datamodel.CpkDbChangeLogIdentifier
import net.corda.libs.cpi.datamodel.repository.CpkDbChangeLogRepository
import net.corda.libs.cpi.datamodel.repository.CpkDbChangeLogRepositoryImpl
import net.corda.libs.virtualnode.common.constant.VirtualNodeStateTransitions
import net.corda.libs.virtualnode.common.exception.InvalidStateChangeRuntimeException
import net.corda.libs.virtualnode.common.exception.VirtualNodeNotFoundException
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepository
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepositoryImpl
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.schema.Schemas.VirtualNode.VIRTUAL_NODE_INFO_TOPIC
import net.corda.virtualnode.OperationalStatus
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.MigrationUtility
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.handlers.VirtualNodeOperationStatusHandler
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.Locale
import java.util.concurrent.CompletableFuture
import javax.persistence.EntityManager
import javax.sql.DataSource
import net.corda.v5.crypto.SecureHash

/**
 * An RPC responder processor that handles virtual node creation requests.
 *
 * For each virtual node creation request, persists the created virtual node to the cluster database publishes it to
 * Kafka.
 *
 * @property vNodePublisher Used to publish to Kafka.
 * @property oldVirtualNodeEntityRepository Used to retrieve and store virtual nodes and related entities.
 *           than always simply the system wall clock time so that we can control everything in tests.
 */
@Suppress("LongParameterList", "TooManyFunctions", "LongMethod")
internal class VirtualNodeWriterProcessor(
    private val vNodePublisher: Publisher,
    private val dbConnectionManager: DbConnectionManager,
    private val oldVirtualNodeEntityRepository: VirtualNodeEntityRepository,
    private val virtualNodeOperationStatusHandler: VirtualNodeOperationStatusHandler,
    private val changeLogsRepository: CpkDbChangeLogRepository,
    private val virtualNodeRepository: VirtualNodeRepository = VirtualNodeRepositoryImpl(),
    private val migrationUtility: MigrationUtility
) : RPCResponderProcessor<VirtualNodeManagementRequest, VirtualNodeManagementResponse> {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        val systemTerminatorTag = "${VAULT.name}-system-final"
        val cpkDbChangeLogRepository = CpkDbChangeLogRepositoryImpl()
    }

    /**
     * For each [request], the processor attempts to commit a new virtual node to the cluster database. If successful,
     * the created virtual node is then published by the [vNodePublisher] to the `VIRTUAL_NODE_INFO_TOPIC` topic.
     *
     * If both steps succeed, [respFuture] is completed successfully. Otherwise, it is completed unsuccessfully.
     */
    override fun onNext(
        request: VirtualNodeManagementRequest,
        respFuture: CompletableFuture<VirtualNodeManagementResponse>
    ) {
        when (val typedRequest = request.request) {
            is VirtualNodeStateChangeRequest -> {
                logger.info(
                    "Handling change virtual node state request for ${typedRequest.holdingIdentityShortHash} to ${typedRequest.newState}"
                )
                changeVirtualNodeState(request.timestamp, typedRequest, respFuture)
            }
            is VirtualNodeDBResetRequest -> {
                logger.info("Handling virtual node db reset request for ${typedRequest.holdingIdentityShortHashes.joinToString()}")
                resetVirtualNodeDb(request.timestamp, typedRequest, respFuture)
            }
            is VirtualNodeOperationStatusRequest -> {
                logger.info("Handling virtual node operation status request with id ${typedRequest.requestId}")
                virtualNodeOperationStatusHandler.handle(request.timestamp, typedRequest, respFuture)
            }
            else -> throw VirtualNodeWriteServiceException("Unknown management request of type: ${typedRequest::class.java.name}")
        }
    }

    private fun resetVirtualNodeDb(
        instant: Instant,
        dbResetRequest: VirtualNodeDBResetRequest,
        respFuture: CompletableFuture<VirtualNodeManagementResponse>
    ) {
        val em = dbConnectionManager.getClusterEntityManagerFactory().createEntityManager()
        val shortHashes = em.use {
            dbResetRequest.holdingIdentityShortHashes.map { currentVNodeShortHash ->
                val shortHash = ShortHash.Companion.of(currentVNodeShortHash)
                // Open a TX to find the connection information we need for the virtual nodes vault as it may live on
                //  another database.
                it.transaction { em ->
                    // Retrieve virtual node info
                    val virtualNodeInfo = virtualNodeRepository.find(em, shortHash)
                    if (null == virtualNodeInfo) {
                        logger.warn("Could not find the virtual node: $currentVNodeShortHash")
                        respFuture.complete(
                            VirtualNodeManagementResponse(
                                instant,
                                VirtualNodeManagementResponseFailure(
                                    ExceptionEnvelope(
                                        VirtualNodeNotFoundException::class.java.name,
                                        "Could not find the virtual node: $currentVNodeShortHash"
                                    )
                                )
                            )
                        )
                        return
                    }

                    val cpiMetadataLite = oldVirtualNodeEntityRepository.getCPIMetadataById(
                        virtualNodeInfo.cpiIdentifier.name,
                        virtualNodeInfo.cpiIdentifier.version
                    )!!
                    dbConnectionManager.createDatasource(virtualNodeInfo.vaultDdlConnectionId!!).use { dataSource ->
                        // changelog tags are the CPK file checksum the changelog belongs to
                        val cpkChecksumsOfAppliedChangelogs: Set<String> = getAppliedChangelogTags(
                            em,
                            dataSource,
                            systemTerminatorTag
                        )

                        logger.info(
                            "CPK file checksums of currently applied changelogs on vault schema for virtual node " +
                                    "$currentVNodeShortHash: [${cpkChecksumsOfAppliedChangelogs.joinToString()}]"
                        )

                        val changesetsToRollback =
                            cpkDbChangeLogRepository.findByFileChecksum(em, cpkChecksumsOfAppliedChangelogs)
                                .groupBy { it.id.cpkFileChecksum }

                        changesetsToRollback.forEach { (cpkFileChecksum, changelogs) ->
                            val changeLogs = changelogs.joinToString { it.id.filePath }
                            logger.info(
                                "Virtual node '$currentVNodeShortHash' attempting to roll back the following " +
                                        "changelogs for CPK '$cpkFileChecksum' [$changeLogs]"
                            )
                            rollbackVirtualNodeDb(
                                dbConnectionManager.createDatasource(virtualNodeInfo.vaultDdlConnectionId!!),
                                changelogs,
                                systemTerminatorTag
                            )
                            logger.info("Migrations for CPK '$cpkFileChecksum' successfully rolled back.")
                        }
                    }

                    val changelogsToRun =
                        changeLogsRepository.findByCpiId(em, cpiMetadataLite.id).groupBy { it.id.cpkFileChecksum }

                    changelogsToRun.forEach { (cpkFileChecksum, changelogsForThisCpk) ->
                        try {
                            dbConnectionManager.createDatasource(virtualNodeInfo.vaultDdlConnectionId!!).use {
                                runCpkResyncMigrations(it, cpkFileChecksum, changelogsForThisCpk)
                            }
                        } catch (e: Exception) {
                            val changeLogs = changelogsForThisCpk.joinToString {
                                it.id.cpkFileChecksum.toString() + ", " + it.id.filePath
                            }
                            logger.warn(
                                "Error from liquibase API while running resync migrations for CPI " +
                                        "${cpiMetadataLite.id.name} - changelogs: [${changeLogs}]",
                                e
                            )
                            respFuture.complete(
                                VirtualNodeManagementResponse(
                                    instant,
                                    VirtualNodeManagementResponseFailure(
                                        ExceptionEnvelope(
                                            VirtualNodeWriteServiceException::class.java.name,
                                            e.message
                                        )
                                    )
                                )
                            )
                        }
                    }
                }
                shortHash.value
            }
        }

        respFuture.complete(
            VirtualNodeManagementResponse(
                instant,
                VirtualNodeDBResetResponse(shortHashes)
            )
        )
    }

    private fun rollbackVirtualNodeDb(
        dataSource: CloseableDataSource,
        changelogs: List<CpkDbChangeLog>,
        tagToRollbackTo: String
    ) {
        val dbChange = VirtualNodeDbChangeLog(changelogs)
        val connection = dataSource.connection
        LiquibaseSchemaMigratorImpl().rollBackDb(connection, dbChange, tagToRollbackTo)
    }

    // State change request produced by VirtualNodeMaintenanceRPCOpsImpl
    @Suppress("NestedBlockDepth", "ThrowsCount")
    private fun changeVirtualNodeState(
        instant: Instant,
        stateChangeRequest: VirtualNodeStateChangeRequest,
        respFuture: CompletableFuture<VirtualNodeManagementResponse>
    ) {

        // Attempt and update, and on failure, pass the error back to the RPC processor
        try {
            val em = dbConnectionManager.getClusterEntityManagerFactory().createEntityManager()

            val updatedVirtualNode = em.use { entityManager ->

                val shortHash = ShortHash.Companion.of(stateChangeRequest.holdingIdentityShortHash)

                val nodeInfo = virtualNodeRepository.find(entityManager, shortHash)
                    ?: throw VirtualNodeDbException("Unable to fetch node info")

                val inMaintenance = listOf(
                    nodeInfo.flowOperationalStatus,
                    nodeInfo.flowStartOperationalStatus,
                    nodeInfo.flowP2pOperationalStatus,
                    nodeInfo.vaultDbOperationalStatus
                ).any { it == OperationalStatus.INACTIVE }

                val newState = VirtualNodeStateTransitions.valueOf(stateChangeRequest.newState.uppercase())

                // Compare new state to current state
                when (inMaintenance) {
                    true -> if (newState == VirtualNodeStateTransitions.MAINTENANCE)
                        throw InvalidStateChangeRuntimeException("VirtualNode", shortHash.value, newState.name)

                    false -> if (newState == VirtualNodeStateTransitions.ACTIVE)
                        throw InvalidStateChangeRuntimeException("VirtualNode", shortHash.value, newState.name)
                }

                val changelogsPerCpk = changeLogsRepository.findByCpiId(em, nodeInfo.cpiIdentifier)
                if (stateChangeRequest.newState.lowercase(Locale.getDefault()) == "active") {
                    val inSync = migrationUtility.isVaultSchemaAndTargetCpiInSync(
                        stateChangeRequest.holdingIdentityShortHash, changelogsPerCpk, nodeInfo.vaultDmlConnectionId
                    )
                    if (!inSync) {
                        logger.info("Cannot set state to ACTIVE, db is not in sync with changelogs")
                        throw VirtualNodeDbException("Cannot set state to ACTIVE, db is not in sync with changelogs")
                    }
                }

                virtualNodeRepository.updateVirtualNodeState(
                    entityManager,
                    stateChangeRequest.holdingIdentityShortHash,
                    stateChangeRequest.newState
                )
            }

            val avroPayload = updatedVirtualNode.toAvro()

            val virtualNodeRecord = Record(
                VIRTUAL_NODE_INFO_TOPIC,
                avroPayload.holdingIdentity,
                avroPayload
            )

            try {
                // TODO - CORE-3319 - Strategy for DB and Kafka retries.
                val future = vNodePublisher.publish(listOf(virtualNodeRecord)).first()

                // TODO - CORE-3730 - Define timeout policy.
                future.get()
            } catch (e: Exception) {
                throw VirtualNodeWriteServiceException(
                    "Record $virtualNodeRecord was written to the database, but couldn't be published. Cause: $e", e
                )
            }

            val response = VirtualNodeManagementResponse(
                instant,
                VirtualNodeStateChangeResponse(
                    stateChangeRequest.holdingIdentityShortHash,
                    VirtualNodeInfo.DEFAULT_INITIAL_STATE.name,
                    VirtualNodeInfo.DEFAULT_INITIAL_STATE.name,
                    VirtualNodeInfo.DEFAULT_INITIAL_STATE.name,
                    VirtualNodeInfo.DEFAULT_INITIAL_STATE.name
                )
            )
            respFuture.complete(response)
        } catch (e: Exception) {
            respFuture.complete(
                VirtualNodeManagementResponse(
                    instant,
                    VirtualNodeManagementResponseFailure(
                        ExceptionEnvelope(
                            e::class.java.name,
                            e.message
                        )
                    )
                )
            )
        }
    }

    private fun runCpkResyncMigrations(
        dataSource: CloseableDataSource,
        cpkFileChecksum: SecureHash,
        changelogs: List<CpkDbChangeLog>
    ) {
        if (changelogs.isEmpty()) return

        logger.info("Preparing to run ${changelogs.size} resync migrations for CPK '$cpkFileChecksum'.")

        LiquibaseSchemaMigratorImpl().updateDb(
            dataSource.connection,
            VirtualNodeDbChangeLog(changelogs.map {
                CpkDbChangeLog(
                    CpkDbChangeLogIdentifier(
                        it.id.cpkFileChecksum,
                        it.id.filePath
                    ), it.content
                )
            }),
            tag = cpkFileChecksum.toString()
        )
        logger.info("Resync migrations for CPK '$cpkFileChecksum' completed.")
    }

    @Suppress("UNCHECKED_CAST")
    private fun getAppliedChangelogTags(
        em: EntityManager,
        dataSource: DataSource,
        systemTerminatorTag: String
    ): Set<String> = (
            em.createNativeQuery(
                "SELECT tag FROM ${dataSource.connection.schema}.databasechangelog " +
                        "WHERE tag IS NOT NULL and tag != :systemTerminatorTag " +
                        "ORDER BY orderexecuted"
            )
                .setParameter("systemTerminatorTag", systemTerminatorTag)
                .resultList
                .toSet() as Set<String>
            ).toSet()
}
