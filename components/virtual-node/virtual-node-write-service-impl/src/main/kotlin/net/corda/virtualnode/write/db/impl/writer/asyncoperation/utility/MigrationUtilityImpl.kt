package net.corda.virtualnode.write.db.impl.writer.asyncoperation.utility

import java.util.UUID
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.CloseableDataSource
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.cpi.datamodel.CpkDbChangeLogEntity
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbChangeLog
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.MigrationUtility
import org.slf4j.LoggerFactory

internal class MigrationUtilityImpl(
    private val dbConnectionManager: DbConnectionManager,
    private val liquibaseSchemaMigrator: LiquibaseSchemaMigrator,
) : MigrationUtility {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun runVaultMigrations(
        virtualNodeShortHash: ShortHash,
        migrationChangeLogs: List<CpkDbChangeLogEntity>,
        vaultDdlConnectionId: UUID
    ) {
        migrationChangeLogs
            .groupBy { it.id.cpkFileChecksum }
            .forEach { (cpkFileChecksum, changelogs) ->
                dbConnectionManager.createDatasource(vaultDdlConnectionId).use {
                    runCpkMigrations(it, virtualNodeShortHash, cpkFileChecksum, changelogs)
                }
            }
    }

    override fun isVaultSchemaAndTargetCpiInSync(cpkChangelogs: List<CpkDbChangeLogEntity>, vaultDmlConnectionId: UUID): Boolean {
        // todo cs - as part of CORE-https://r3-cev.atlassian.net/browse/CORE-9046
        return false
    }

    private fun runCpkMigrations(
        dataSource: CloseableDataSource, virtualNodeShortHash: ShortHash, cpkFileChecksum: String, changeLogs: List<CpkDbChangeLogEntity>
    ) {
        logger.info("Preparing to run ${changeLogs.size} migrations for CPK '$cpkFileChecksum'.")
        val allChangeLogsForCpk = VirtualNodeDbChangeLog(changeLogs.map { CpkDbChangeLog(it.id.filePath, it.content) })
        try {
            liquibaseSchemaMigrator.updateDb(dataSource.connection, allChangeLogsForCpk, tag = cpkFileChecksum)
        } catch (e: Exception) {
            val msg =
                "CPI migrations failed for virtual node '$virtualNodeShortHash`. Failure occurred running CPI migrations on " +
                        "CPK with file checksum $cpkFileChecksum."
            logger.warn(msg, e)
            throw VirtualNodeWriteServiceException(msg, e)
        }
        logger.info("Successfully completed ${changeLogs.size} migrations for CPK with file checksum $cpkFileChecksum.")
    }
}