package net.corda.virtualnode.write.db.impl.writer.asyncoperation.services

import net.corda.data.virtualnode.VirtualNodeCreateRequest
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.cpi.datamodel.repository.CpkDbChangeLogRepository
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.virtualnode.common.exception.CpiNotFoundException
import net.corda.libs.virtualnode.common.exception.VirtualNodeAlreadyExistsException
import net.corda.libs.virtualnode.datamodel.repository.HoldingIdentityRepository
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepository
import net.corda.messaging.api.publisher.Publisher
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import net.corda.virtualnode.write.db.impl.writer.CpiEntityRepository
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDb
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbChangeLog
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
internal class CreateVirtualNodeServiceImpl(
    private val dbConnectionManager: DbConnectionManager,
    private val cpkDbChangeLogRepository: CpkDbChangeLogRepository,
    private val cpiEntityRepository: CpiEntityRepository,
    private val virtualNodeRepository: VirtualNodeRepository,
    private val holdingIdentityRepository: HoldingIdentityRepository,
    publisher: Publisher,
) : CreateVirtualNodeService, AbstractVirtualNodeService(
    dbConnectionManager,
    holdingIdentityRepository,
    virtualNodeRepository,
    publisher
) {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
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
        emf.createEntityManager().use { em ->
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

    override fun getCpiMetaData(cpiFileChecksum: String): CpiMetadata {
        return cpiEntityRepository.getCpiMetadataByChecksum(cpiFileChecksum)
            ?: throw CpiNotFoundException("CPI with file checksum $cpiFileChecksum was not found.")
    }

    override fun runCpiMigrations(
        cpiMetadata: CpiMetadata,
        vaultDb: VirtualNodeDb,
        holdingIdentity: HoldingIdentity
    ) {
        dbConnectionManager.getClusterEntityManagerFactory().createEntityManager().transaction { em ->

            val changelogsPerCpk = cpkDbChangeLogRepository.findByCpiId(em, cpiMetadata.cpiId)
                .groupBy { it.id.cpkFileChecksum }

            changelogsPerCpk.forEach { (cpkFileChecksum, changeLogs) ->
                logger.info("Preparing to run ${changeLogs.size} migrations for CPK '$cpkFileChecksum'.")
                val allChangeLogsForCpk =
                    VirtualNodeDbChangeLog(changeLogs.map { CpkDbChangeLog(it.id, it.content) })
                try {
                    vaultDb.runCpiMigrations(allChangeLogsForCpk, cpkFileChecksum.toString())
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

    override fun checkCpiMigrations(
        cpiMetadata: CpiMetadata,
        vaultDb: VirtualNodeDb,
        holdingIdentity: HoldingIdentity
    ): Boolean {
        dbConnectionManager.getClusterEntityManagerFactory().createEntityManager().use { em ->

            val changelogsPerCpk = cpkDbChangeLogRepository.findByCpiId(em, cpiMetadata.cpiId)
                .groupBy { it.id.cpkFileChecksum }

            return changelogsPerCpk.all { (_, changeLogs) ->
                val allChangeLogsForCpk =
                    VirtualNodeDbChangeLog(changeLogs.map { CpkDbChangeLog(it.id, it.content) })
                vaultDb.checkCpiMigrationsArePresent(allChangeLogsForCpk)
            }
        }
    }
}
