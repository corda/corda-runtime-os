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
import net.corda.libs.cpi.datamodel.repository.factory.CpiCpkRepositoryFactory
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepository
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepositoryImpl
import net.corda.orm.utils.transaction
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbChangeLog
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbException
import java.io.StringWriter
import java.sql.Connection
import java.time.Instant
import java.util.concurrent.CompletableFuture
import javax.persistence.EntityManager

internal class VirtualNodeSchemaHandler(
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
        val connection = dbConnectionManager.getClusterDataSource().connection
        val sql = when (virtualNodeSchemaRequest.dbType) {
            DbTypes.CRYPTO, DbTypes.UNIQUENESS -> {
                val changelog = getChangelog(virtualNodeSchemaRequest.dbType)
                buildSqlWithStringWriter(connection, changelog)
            }

            DbTypes.VAULT -> {
                if (virtualNodeSchemaRequest.virtualNodeShortHash == null && virtualNodeSchemaRequest.cpiChecksum != null) {
                    val changeLog = getChangelog(virtualNodeSchemaRequest.dbType)
                    dbConnectionManager.getClusterEntityManagerFactory().createEntityManager().transaction { em ->
                        val cpkChangeLog = getCpkChangelog(em, virtualNodeSchemaRequest.cpiChecksum)
                        buildSqlWithStringWriter(connection, changeLog) + buildSqlWithStringWriter(
                            connection, cpkChangeLog
                        )
                    }
                } else if (virtualNodeSchemaRequest.virtualNodeShortHash != null && virtualNodeSchemaRequest.cpiChecksum != null) {
                    dbConnectionManager.getClusterEntityManagerFactory().createEntityManager().transaction { em ->
                        val virtualNodeInfo = virtualNodeRepository.find(
                            em, ShortHash.parse(virtualNodeSchemaRequest.virtualNodeShortHash)
                        ) ?: throw VirtualNodeDbException("Unable to fetch virtual node info")

                        val cpkChangeLog = getCpkChangelog(em, virtualNodeSchemaRequest.cpiChecksum)
                        val connectionVNodeVault =
                            dbConnectionManager.createDatasource(virtualNodeInfo.vaultDdlConnectionId!!).connection
                        buildSqlWithStringWriter(
                            connectionVNodeVault,
                            cpkChangeLog
                        )
                    }
                } else {
                    throw IllegalArgumentException("Illegal argument combination for virtualNodeSchema")
                }
            }

            else -> throw IllegalArgumentException("Cannot use dbType that does not exist")
        }
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

    private fun getChangelog(dbType: DbTypes): DbChange {
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

    private fun getCpkChangelog(em: EntityManager, cpiChecksum: String): DbChange {
        val cpkDbChangeLogRepository = CpiCpkRepositoryFactory().createCpkDbChangeLogRepository()
        val cpiMetadata = CpiCpkRepositoryFactory().createCpiMetadataRepository().findByFileChecksum(em, cpiChecksum)
        val changelogsPerCpk = cpkDbChangeLogRepository.findByCpiId(em, cpiMetadata!!.cpiId)
        return VirtualNodeDbChangeLog(changelogsPerCpk)
    }

    private fun buildSqlWithStringWriter(
        connection: Connection,
        dbChange: DbChange
    ): String {
        StringWriter().use { writer ->
            schemaMigrator.createUpdateSql(connection, dbChange, writer)
            return writer.toString()
        }
    }
}
