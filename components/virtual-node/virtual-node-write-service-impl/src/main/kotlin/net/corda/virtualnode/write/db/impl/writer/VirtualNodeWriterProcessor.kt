package net.corda.virtualnode.write.db.impl.writer

import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import javax.persistence.EntityManager
import javax.sql.DataSource
import net.corda.data.ExceptionEnvelope
import net.corda.data.virtualnode.VirtualNodeCreateRequest
import net.corda.data.virtualnode.VirtualNodeDBResetRequest
import net.corda.data.virtualnode.VirtualNodeDBResetResponse
import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.data.virtualnode.VirtualNodeManagementResponseFailure
import net.corda.data.virtualnode.VirtualNodeStateChangeRequest
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.VirtualNodeDbType.VAULT
import net.corda.db.core.CloseableDataSource
import net.corda.libs.cpi.datamodel.CpkDbChangeLogAuditEntity
import net.corda.libs.cpi.datamodel.CpkDbChangeLogEntity
import net.corda.libs.cpi.datamodel.findDbChangeLogAuditForCpi
import net.corda.libs.cpi.datamodel.findDbChangeLogForCpi
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.utilities.time.Clock
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import net.corda.virtualnode.write.db.impl.writer.management.VirtualNodeManagementHandler

internal class VirtualNodeWriterProcessor(
    private val createVirtualNodeHandler: VirtualNodeManagementHandler<VirtualNodeCreateRequest>,
    private val changeVirtualNodeStateHandler: VirtualNodeManagementHandler<VirtualNodeStateChangeRequest>,
    private val vnodePublisher: Publisher,
    private val dbConnectionManager: DbConnectionManager,
    private val virtualNodeEntityRepository: VirtualNodeEntityRepository,
    private val vnodeDbFactory: VirtualNodeDbFactory,
    private val groupPolicyParser: GroupPolicyParser,
    private val clock: Clock,
    private val getChangelogs: (EntityManager, CpiIdentifier) -> List<CpkDbChangeLogEntity> = ::findDbChangeLogForCpi
) : RPCResponderProcessor<VirtualNodeManagementRequest, VirtualNodeManagementResponse> {

    companion object {
        private val logger = contextLogger()
        val systemTerminatorTag = "${VAULT.name}-system-final"
    }

    private fun resetVirtualNodeDb(
        instant: Instant,
        dbResetRequest: VirtualNodeDBResetRequest,
        respFuture: CompletableFuture<VirtualNodeManagementResponse>
    ) {
        val em = dbConnectionManager.getClusterEntityManagerFactory().createEntityManager()
        val shortHashes = em.use {
            dbResetRequest.holdingIdentityShortHashes.map { shortHashString ->
                val shortHash = ShortHash.Companion.of(shortHashString)
                // Open a TX to find the connection information we need for the virtual nodes vault as it may live on
                //  another database.
                it.transaction { tx ->
                    // Retrieve virtual node info
                    val virtualNodeInfo = try {
                        virtualNodeEntityRepository.getVirtualNode(shortHashString)
                    } catch (e: VirtualNodeNotFoundException) {
                        logger.warn("Could not find the virtual node: $shortHashString", e)
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
                        return
                    }
                    // Retrieve CPI metadata
                    val cpiMetadata = virtualNodeEntityRepository.getCPIMetadataByNameAndVersion(
                        virtualNodeInfo.cpiIdentifier.name,
                        virtualNodeInfo.cpiIdentifier.version
                    )!!
                    dbConnectionManager.createDatasource(virtualNodeInfo.vaultDdlConnectionId!!).use { dataSource ->
                        // Look up the tags(UUIDs) of the applied changelog entries
                        val appliedVersions: Set<UUID> = getAppliedVersions(
                            tx,
                            dataSource,
                            systemTerminatorTag
                        )
                        // Look up all audit entries that correspond to the UUID set that we just got
                        val migrationSet = findDbChangeLogAuditForCpi(tx, virtualNodeInfo.cpiIdentifier, appliedVersions)
                        // Attempt to rollback the acquired changes
                        rollbackVirtualNodeDb(
                            dbConnectionManager.createDatasource(virtualNodeInfo.vaultDdlConnectionId!!),
                            migrationSet,
                            systemTerminatorTag
                        )
                    }
                    logger.info("Finished rolling back previous migrations, attempting to apply new ones")
                    // Attempt to apply the changes from the current CPI
                    val changelogs = getChangelogs(em, cpiMetadata.id)
                    dbConnectionManager.createDatasource(virtualNodeInfo.vaultDdlConnectionId!!).use { dataSource ->
                        runCpiResyncMigrations(
                            dataSource,
                            changelogs
                        )
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
        changelogs: List<CpkDbChangeLogAuditEntity>,
        tagToRollbackTo: String
    ) {
        val dbChange = VirtualNodeDbChangeLog(changelogs)
        val connection = dataSource.connection
        LiquibaseSchemaMigratorImpl().rollBackDb(connection, dbChange, tagToRollbackTo)
    }

    /**
     * For each management request the processor calls out to the corresponding handler to perform the operation.
     */
    override fun onNext(
        request: VirtualNodeManagementRequest,
        respFuture: CompletableFuture<VirtualNodeManagementResponse>
    ) {
        when (val typedRequest = request.request) {
            is VirtualNodeCreateRequest -> createVirtualNodeHandler.handle(request.timestamp, typedRequest, respFuture)
            is VirtualNodeStateChangeRequest -> changeVirtualNodeStateHandler.handle(request.timestamp, typedRequest, respFuture)
            is VirtualNodeDBResetRequest -> resetVirtualNodeDb(request.timestamp, typedRequest, respFuture)
            else -> throw VirtualNodeWriteServiceException("Unknown management request of type: ${typedRequest::class.java.name}")
        }
    }

    private fun runCpiResyncMigrations(dataSource: CloseableDataSource, changelogs: List<CpkDbChangeLogEntity>) {
        changelogs.map { cl -> cl.id.cpkName }.distinct().sorted().forEach { cpkName ->
            val cpkChangelogs = changelogs.filter { cl2 -> cl2.id.cpkName == cpkName }
            val newChangeSetId = cpkChangelogs.first().changesetId
            logger.info("Applying change logs from $cpkName at $newChangeSetId")
            val connection = dataSource.connection
            LiquibaseSchemaMigratorImpl().updateDb(
                connection,
                VirtualNodeDbChangeLog(cpkChangelogs),
                tag = newChangeSetId.toString()
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getAppliedVersions(em: EntityManager, dataSource: DataSource, systemTerminatorTag: String): Set<UUID> = (
        em.createNativeQuery(
            "SELECT tag FROM ${dataSource.connection.schema}.databasechangelog " +
                "WHERE tag IS NOT NULL and tag != :systemTerminatorTag " +
                "ORDER BY orderexecuted"
        )
            .setParameter("systemTerminatorTag", systemTerminatorTag)
            .resultList
            .toSet() as Set<String>
        ).map { UUID.fromString(it) }.toSet()

}
