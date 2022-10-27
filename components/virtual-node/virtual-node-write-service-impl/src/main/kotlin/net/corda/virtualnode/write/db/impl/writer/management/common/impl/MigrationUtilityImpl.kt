package net.corda.virtualnode.write.db.impl.writer.management.common.impl

import javax.persistence.EntityManager
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.cpi.datamodel.CpkDbChangeLogEntity
import net.corda.libs.cpi.datamodel.findDbChangeLogForCpi
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.orm.utils.transaction
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import net.corda.virtualnode.write.db.impl.writer.CpiMetadataLite
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDb
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbChangeLog
import net.corda.virtualnode.write.db.impl.writer.management.common.MigrationUtility

internal class MigrationUtilityImpl(
    private val dbConnectionManager: DbConnectionManager,
    private val getChangelogs: (EntityManager, CpiIdentifier) -> List<CpkDbChangeLogEntity> = ::findDbChangeLogForCpi
): MigrationUtility {

    companion object {
        private val logger = contextLogger()
    }

    override fun runCpiMigrations(cpiMetadata: CpiMetadataLite, vaultDb: VirtualNodeDb) =
    // we could potentially do one transaction per CPK; it seems more useful to blow up the
        // who migration if any CPK fails though, so that they can be iterative developed and repeated
        dbConnectionManager.getClusterEntityManagerFactory().createEntityManager().transaction {
            val changelogs = getChangelogs(it, cpiMetadata.id)
            changelogs.map { cl -> cl.id.cpkName }.distinct().sorted().forEach { cpkName ->
                val cpkChangelogs = changelogs.filter { cl2 -> cl2.id.cpkName == cpkName }
                logger.info("Doing ${cpkChangelogs.size} migrations for $cpkName")
                val dbChange = VirtualNodeDbChangeLog(cpkChangelogs)
                try {
                    vaultDb.runCpiMigrations(dbChange)
                } catch (e: Exception) {
                    logger.error("Virtual node liquibase DB migration failure on CPK $cpkName with error $e")
                    throw VirtualNodeWriteServiceException(
                        "Error running virtual node DB migration for CPI liquibase migrations",
                        e
                    )
                }
                logger.info("Completed ${cpkChangelogs.size} migrations for $cpkName")
            }
        }
}