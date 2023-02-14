package net.corda.virtualnode.write.db.impl.writer.asyncoperation.services

import net.corda.data.virtualnode.VirtualNodeCreateRequest
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.VirtualNodeDbType
import net.corda.db.connection.manager.VirtualNodeDbType.CRYPTO
import net.corda.db.connection.manager.VirtualNodeDbType.UNIQUENESS
import net.corda.db.connection.manager.VirtualNodeDbType.VAULT
import net.corda.db.core.DbPrivilege
import net.corda.db.core.DbPrivilege.DDL
import net.corda.db.core.DbPrivilege.DML
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.cpi.datamodel.repository.CpkDbChangeLogRepository
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.virtualnode.common.exception.CpiNotFoundException
import net.corda.libs.virtualnode.common.exception.VirtualNodeAlreadyExistsException
import net.corda.libs.virtualnode.datamodel.repository.HoldingIdentityRepository
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepository
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import net.corda.virtualnode.write.db.impl.writer.CpiEntityRepository
import net.corda.virtualnode.write.db.impl.writer.CpiMetadataLite
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDb
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbChangeLog
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbConnections
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.persistence.EntityManager

@Suppress("LongParameterList")
internal class CreateVirtualNodeServiceImpl(
    private val dbConnectionManager: DbConnectionManager,
    private val cpkDbChangeLogRepository: CpkDbChangeLogRepository,
    private val cpiEntityRepository: CpiEntityRepository,
    private val virtualNodeRepository: VirtualNodeRepository,
    private val holdingIdentityRepository: HoldingIdentityRepository,
    private val publisher: Publisher,
) : CreateVirtualNodeService {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val PUBLICATION_TIMEOUT_SECONDS = 30L
    }

    override fun validateRequest(request: VirtualNodeCreateRequest): String? {
        with(request) {
            if (cpiFileChecksum.isNullOrBlank()) {
                return "CPI file checksum value is missing"
            }

            if (!vaultDdlConnection.isNullOrBlank() && vaultDmlConnection.isNullOrBlank()) {
                return "If Vault DDL connection is provided, Vault DML connection needs to be provided as well."
            }

            if (!cryptoDdlConnection.isNullOrBlank() && cryptoDmlConnection.isNullOrBlank()) {
                return "If Crypto DDL connection is provided, Crypto DML connection needs to be provided as well."
            }

            if (!uniquenessDdlConnection.isNullOrBlank() && uniquenessDmlConnection.isNullOrBlank()) {
                return "If Uniqueness DDL connection is provided, Uniqueness DML connection needs to be provided as well."
            }
        }
        return null
    }

    override fun ensureHoldingIdentityIsUnique(request: VirtualNodeCreateRequest) {
        val emf = dbConnectionManager.getClusterEntityManagerFactory()
        val holdingIdShortHash = request.holdingId.toCorda().shortHash
        emf.use { em ->
            if (virtualNodeRepository.find(em, holdingIdShortHash) != null) {
                throw VirtualNodeAlreadyExistsException(
                    "Virtual node for CPI with file checksum ${request.cpiFileChecksum} and x500Name " +
                            "${request.holdingId.x500Name} already exists."
                )
            }

            if (holdingIdentityRepository.find(em, holdingIdShortHash) != null) {
                throw VirtualNodeWriteServiceException(
                    "New holding identity ${request.holdingId} has a short hash that collided with existing holding identity."
                )
            }
        }
    }

    override fun getCpiMetaData(cpiFileChecksum: String): CpiMetadataLite {
        return cpiEntityRepository.getCpiMetadataByChecksum(cpiFileChecksum)
            ?: throw  CpiNotFoundException("CPI with file checksum ${cpiFileChecksum} was not found.")
    }

    override fun runCpiMigrations(
        cpiMetadata: CpiMetadataLite,
        vaultDb: VirtualNodeDb,
        holdingIdentity: HoldingIdentity
    ) {
        dbConnectionManager.getClusterEntityManagerFactory().createEntityManager().transaction { em ->

            val changelogsPerCpk = cpkDbChangeLogRepository.findByCpiId(em, cpiMetadata.id)
                .groupBy { it.id.cpkFileChecksum }

            changelogsPerCpk.forEach { (cpkFileChecksum, changeLogs) ->
                logger.info("Preparing to run ${changeLogs.size} migrations for CPK '$cpkFileChecksum'.")
                val allChangeLogsForCpk =
                    VirtualNodeDbChangeLog(changeLogs.map { CpkDbChangeLog(it.id, it.content) })
                try {
                    vaultDb.runCpiMigrations(allChangeLogsForCpk, cpkFileChecksum)
                } catch (e: Exception) {
                    val msg = "CPI migrations failed for virtual node '${holdingIdentity.shortHash}`. Failure " +
                            "occurred running CPI migrations on CPK with file checksum $cpkFileChecksum."
                    logger.warn(msg, e)
                    throw VirtualNodeWriteServiceException(msg, e)
                }
                logger.info(
                    "Successfully completed ${changeLogs.size} migrations for CPK with file checksum $cpkFileChecksum."
                )
            }
        }
    }

    override fun persistHoldingIdAndVirtualNode(
        holdingIdentity: HoldingIdentity,
        vNodeDbs: Map<VirtualNodeDbType, VirtualNodeDb>,
        cpiId: CpiIdentifier,
        updateActor: String
    ): VirtualNodeDbConnections {
        try {
            return dbConnectionManager.getClusterEntityManagerFactory().createEntityManager().transaction { em ->
                val dbConnections = VirtualNodeDbConnections(
                    vaultDdlConnectionId = persistConnection(em, vNodeDbs, VAULT, DDL, updateActor),
                    vaultDmlConnectionId = persistConnection(em, vNodeDbs, VAULT, DML, updateActor)!!,
                    cryptoDdlConnectionId = persistConnection(em, vNodeDbs, CRYPTO, DDL, updateActor),
                    cryptoDmlConnectionId = persistConnection(em, vNodeDbs, CRYPTO, DML, updateActor)!!,
                    uniquenessDdlConnectionId = persistConnection(em, vNodeDbs, UNIQUENESS, DDL, updateActor),
                    uniquenessDmlConnectionId = persistConnection(em, vNodeDbs, UNIQUENESS, DML, updateActor)!!
                )

                holdingIdentityRepository.put(em, holdingIdentity)

                virtualNodeRepository.put(
                    em,
                    holdingIdentity,
                    cpiId,
                    dbConnections.vaultDdlConnectionId,
                    dbConnections.vaultDmlConnectionId,
                    dbConnections.cryptoDdlConnectionId,
                    dbConnections.cryptoDmlConnectionId,
                    dbConnections.uniquenessDdlConnectionId,
                    dbConnections.uniquenessDmlConnectionId,
                )

                dbConnections
            }
        } catch (e: Exception) {
            throw VirtualNodeWriteServiceException(
                "Error persisting virtual node for holding identity $holdingIdentity",
                e
            )
        }
    }

    @Suppress("SpreadOperator")
    override fun publishRecords(records: List<Record<*, *>>) {
        // TODO - CORE-3730 - Define timeout policy.
        CompletableFuture
            .allOf(*publisher.publish(records).toTypedArray())
            .get(PUBLICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    private fun persistConnection(
        entityManager: EntityManager,
        vNodeDbs: Map<VirtualNodeDbType, VirtualNodeDb>,
        dbType: VirtualNodeDbType,
        dbPrivilege: DbPrivilege,
        updateActor: String
    ): UUID? {
        return vNodeDbs[dbType]?.let { vNodeDb ->
            vNodeDb.dbConnections[dbPrivilege]?.let { dbConnection ->
                with(dbConnection) {
                    dbConnectionManager.putConnection(
                        entityManager,
                        name,
                        dbPrivilege,
                        config,
                        description,
                        updateActor
                    )
                }
            }
        }
    }
}