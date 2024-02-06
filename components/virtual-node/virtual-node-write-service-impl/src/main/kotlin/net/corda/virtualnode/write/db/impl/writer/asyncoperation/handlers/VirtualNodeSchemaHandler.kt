package net.corda.virtualnode.write.db.impl.writer.asyncoperation.handlers

import net.corda.crypto.core.ShortHash
import net.corda.data.virtualnode.DbTypes
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.data.virtualnode.VirtualNodeSchemaRequest
import net.corda.data.virtualnode.VirtualNodeSchemaResponse
import net.corda.db.admin.DbChange
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.DbSchema
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.cpi.datamodel.repository.factory.CpiCpkRepositoryFactory
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepository
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepositoryImpl
import net.corda.orm.utils.transaction
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbChangeLog
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbException
import java.io.StringWriter
import java.nio.file.Path
import java.sql.Connection
import java.time.Instant
import java.util.concurrent.CompletableFuture
import javax.persistence.EntityManager

internal class VirtualNodeSchemaHandler(
    private val offlineDbDir: Path,
    private val dbConnectionManager: DbConnectionManager,
    private val schemaMigrator: LiquibaseSchemaMigrator,
    private val virtualNodeRepository: VirtualNodeRepository = VirtualNodeRepositoryImpl(),
) {
    @Suppress("ThrowsCount")
    fun handle(
        instant: Instant,
        virtualNodeSchemaRequest: VirtualNodeSchemaRequest,
        respFuture: CompletableFuture<VirtualNodeManagementResponse>
    ) {
        val sql = when (virtualNodeSchemaRequest.dbType) {
            DbTypes.CRYPTO, DbTypes.UNIQUENESS -> {
                val changeLog = getChangeLog(virtualNodeSchemaRequest.dbType)
                buildSqlWithStringWriter(changeLog)
            }

            DbTypes.VAULT -> {
                if (virtualNodeSchemaRequest.virtualNodeShortHash == null && virtualNodeSchemaRequest.cpiChecksum != null) {
                    val changeLog = getChangeLog(virtualNodeSchemaRequest.dbType)
                    dbConnectionManager.getClusterEntityManagerFactory().createEntityManager().transaction { em ->
                        val cpkChangeLogs = getCpkChangeLogs(em, virtualNodeSchemaRequest.cpiChecksum)
                        buildSqlWithStringWriter(changeLog) + buildCpkSqlWithStringWriter(cpkChangeLogs)
                    }
                } else if (virtualNodeSchemaRequest.virtualNodeShortHash != null && virtualNodeSchemaRequest.cpiChecksum != null) {
                    dbConnectionManager.getClusterEntityManagerFactory().createEntityManager().transaction { em ->
                        val virtualNodeInfo = virtualNodeRepository.find(
                            em, ShortHash.parse(virtualNodeSchemaRequest.virtualNodeShortHash)
                        ) ?: throw VirtualNodeDbException("Unable to fetch virtual node info")

                        val cpkChangeLogs = getCpkChangeLogs(em, virtualNodeSchemaRequest.cpiChecksum)
                        val connectionVNodeVault =
                            dbConnectionManager.createDatasource(virtualNodeInfo.vaultDdlConnectionId!!).connection
                        buildCpkSqlWithStringWriter(cpkChangeLogs, connectionVNodeVault)
                    }
                } else {
                    throw IllegalArgumentException("Illegal argument combination for VirtualNodeSchemaRequest")
                }
            }

            else -> throw IllegalArgumentException("Cannot use dbType that does not exist: ${virtualNodeSchemaRequest.dbType}")
        }.replace(
            "CREATE TABLE databasechangelog",
            "CREATE TABLE IF NOT EXISTS databasechangelog"
        )
        respFuture.complete(
            VirtualNodeManagementResponse(
                instant,
                VirtualNodeSchemaResponse(sql)
            )
        )
    }

    private fun dbTypesToString(dbType: DbTypes): String {
        return when (dbType) {
            DbTypes.CRYPTO -> "crypto"
            DbTypes.UNIQUENESS -> "uniqueness"
            DbTypes.VAULT -> "vault"
        }
    }

    private fun getChangeLog(dbType: DbTypes): DbChange {
        val dbTypeAsString = dbTypesToString(dbType)
        val resourceSubPath = "vnode-$dbTypeAsString"
        val schemaClass = DbSchema::class.java
        val fullName = "${schemaClass.packageName}.$resourceSubPath"
        val resourcePrefix = fullName.replace('.', '/')
        val changeLogFiles = ClassloaderChangeLog.ChangeLogResourceFiles(
            fullName,
            listOf("$resourcePrefix/db.changelog-master.xml"),
            classLoader = schemaClass.classLoader
        )
        return ClassloaderChangeLog(linkedSetOf(changeLogFiles))
    }

    private fun getCpkChangeLogs(em: EntityManager, cpiChecksum: String): List<DbChange> {
        val cpkDbChangeLogRepository = CpiCpkRepositoryFactory().createCpkDbChangeLogRepository()
        val cpiMetadata = CpiCpkRepositoryFactory().createCpiMetadataRepository().findByFileChecksum(em, cpiChecksum)
        val changeLogsPerCpk = cpkDbChangeLogRepository.findByCpiId(em, cpiMetadata!!.cpiId)
            .groupBy { it.id.cpkFileChecksum }
        return changeLogsPerCpk.map { (_, changeLogs) ->
            VirtualNodeDbChangeLog(changeLogs.map { CpkDbChangeLog(it.id, it.content) })
        }
    }

    private fun buildSqlWithStringWriter(
        dbChange: DbChange
    ): String {
        StringWriter().use { writer ->
            val offlineDbDirPathString = offlineDbDir.toString()
            schemaMigrator.createUpdateSqlOffline(dbChange, offlineDbDirPathString, writer)
            return writer.toString()
        }
    }

    private fun buildCpkSqlWithStringWriter(
        dbChange: List<DbChange>,
        connection: Connection? = null
    ): String {
        return dbChange.joinToString(separator = "") { cpkChangeLogs ->
            StringWriter().use { writer ->
                if (connection == null) {
                    val offlineDbDirPathString = offlineDbDir.toString()
                    schemaMigrator.createUpdateSqlOffline(cpkChangeLogs, offlineDbDirPathString, writer)
                } else {
                    schemaMigrator.createUpdateSql(connection, cpkChangeLogs, writer)
                }
                writer.toString()
            }
        }
    }
}
