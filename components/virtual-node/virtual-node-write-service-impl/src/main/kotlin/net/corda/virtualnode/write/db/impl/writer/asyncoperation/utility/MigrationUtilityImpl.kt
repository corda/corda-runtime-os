package net.corda.virtualnode.write.db.impl.writer.asyncoperation.utility

import net.corda.crypto.core.ShortHash
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.CloseableDataSource
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.virtualnode.common.exception.LiquibaseDiffCheckFailedException
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbChangeLog
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.MigrationUtility
import org.slf4j.LoggerFactory
import java.util.UUID

internal class MigrationUtilityImpl(
    private val dbConnectionManager: DbConnectionManager,
    private val liquibaseSchemaMigrator: LiquibaseSchemaMigrator,
) : MigrationUtility {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun runVaultMigrations(
        virtualNodeShortHash: ShortHash,
        migrationChangeLogs: List<CpkDbChangeLog>,
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

    override fun areChangesetsDeployedOnVault(
        virtualNodeShortHash: String,
        cpkChangelogs: List<CpkDbChangeLog>,
        vaultDmlConnectionId: UUID
    ): Boolean {
        return try {
            val missingCpks = mutableListOf<String>()
            cpkChangelogs.groupBy { it.id.cpkFileChecksum }.map { (_, changelogs) ->
                val allChangeLogsForCpk = VirtualNodeDbChangeLog(changelogs)
                dbConnectionManager.createDatasource(vaultDmlConnectionId).use { datasource ->
                    missingCpks.addAll(
                        liquibaseSchemaMigrator.listUnrunChangeSets(datasource.connection, allChangeLogsForCpk)
                    )
                }
            }

            if (missingCpks.size > 0) {
                logger.warn(
                    "Found ${missingCpks.size} changelogs missing from virtual node vault $virtualNodeShortHash: " +
                        missingCpks.joinToString()
                )
            }
            missingCpks.size == 0
        } catch (e: Exception) {
            val msg = e.message ?: "Error during Liquibase vault schema diff with CPI for virtual node $virtualNodeShortHash"
            throw LiquibaseDiffCheckFailedException(msg, e)
        }
    }

    private fun runCpkMigrations(
        dataSource: CloseableDataSource,
        virtualNodeShortHash: ShortHash,
        cpkFileChecksum: SecureHash,
        changeLogs: List<CpkDbChangeLog>
    ) {
        logger.info("Preparing to run ${changeLogs.size} migrations for CPK '$cpkFileChecksum'.")
        val allChangeLogsForCpk = VirtualNodeDbChangeLog(changeLogs)
        try {
            dataSource.connection.use { connection ->
                liquibaseSchemaMigrator.updateDb(connection, allChangeLogsForCpk, tag = cpkFileChecksum.toString())
            }
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
