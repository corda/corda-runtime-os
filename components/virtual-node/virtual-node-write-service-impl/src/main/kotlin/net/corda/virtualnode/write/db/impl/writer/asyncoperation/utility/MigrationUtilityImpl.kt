package net.corda.virtualnode.write.db.impl.writer.asyncoperation.utility

import java.util.UUID
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbChangeLog
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.MigrationUtility
import org.slf4j.LoggerFactory
import javax.sql.DataSource

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
                val dataSource = dbConnectionManager.createDatasource(vaultDdlConnectionId)
                runCpkMigrations(dataSource, virtualNodeShortHash, cpkFileChecksum, changelogs)
            }
    }

    override fun isVaultSchemaAndTargetCpiInSync(
        virtualNodeShortHash: String,
        cpkChangelogs: List<CpkDbChangeLog>,
        vaultDmlConnectionId: UUID
    ): Boolean {

        val missingCpks = mutableListOf<String>()
        cpkChangelogs.groupBy { it.id.cpkFileChecksum }.map { (_, changelogs) ->
            val allChangeLogsForCpk = VirtualNodeDbChangeLog(changelogs)
            val datasource = dbConnectionManager.createDatasource(vaultDmlConnectionId)
            datasource.connection.use { connection ->
                missingCpks.addAll(
                    liquibaseSchemaMigrator.listUnrunChangeSets(connection, allChangeLogsForCpk)
                )
            }
        }

        if(missingCpks.size > 0) {
            logger.warn("Found ${missingCpks.size} changelogs missing from virtual node vault $virtualNodeShortHash: " +
                    missingCpks.joinToString()
            )
        }
        return missingCpks.size == 0
    }

    private fun runCpkMigrations(
        dataSource: DataSource, virtualNodeShortHash: ShortHash, cpkFileChecksum: String, changeLogs: List<CpkDbChangeLog>
    ) {
        logger.info("Preparing to run ${changeLogs.size} migrations for CPK '$cpkFileChecksum'.")
        val allChangeLogsForCpk = VirtualNodeDbChangeLog(changeLogs)
        try {
            dataSource.connection.use { connection ->
                liquibaseSchemaMigrator.updateDb(connection, allChangeLogsForCpk, tag = cpkFileChecksum)
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