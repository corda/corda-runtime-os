package net.corda.virtualnode.write.db.impl.writer.asyncoperation.handlers

import net.corda.crypto.core.ShortHash
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.data.virtualnode.VirtualNodeSchemaRequest
import net.corda.data.virtualnode.VirtualNodeSchemaResponse
import net.corda.db.admin.DbChange
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.DbSchema
import net.corda.libs.cpi.datamodel.repository.factory.CpiCpkRepositoryFactory
import net.corda.orm.utils.transaction
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbChangeLog
import java.io.StringWriter
import java.sql.Connection
import java.time.Instant
import java.util.concurrent.CompletableFuture

internal class VirtualNodeSchemaHandler(
    private val dbConnectionManager: DbConnectionManager,
    private val schemaMigrator: LiquibaseSchemaMigrator,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService
) {
    @Suppress("ThrowsCount")
    fun handle(
        instant: Instant,
        virtualNodeSchemaRequest: VirtualNodeSchemaRequest,
        respFuture: CompletableFuture<VirtualNodeManagementResponse>
    ) {
        val connection = dbConnectionManager.getClusterDataSource().connection
        val sql = when (virtualNodeSchemaRequest.dbType) {
            "crypto", "uniqueness" -> {
                val changelog = getChangelog(virtualNodeSchemaRequest.dbType)
                buildSqlWithStringWriter(connection, changelog)
            }

            "vault" -> (
                {
                    if (virtualNodeSchemaRequest.virtualNodeShortHash == null && virtualNodeSchemaRequest.cpiChecksum != null) {
                        val changeLog = getChangelog(virtualNodeSchemaRequest.dbType)
                        val cpkChangeLog = getCpkChangelog(virtualNodeSchemaRequest.dbType)
                        buildSqlWithStringWriter(connection, changeLog) + buildSqlWithStringWriter(
                            connection,
                            cpkChangeLog
                        )
                    } else if (virtualNodeSchemaRequest.virtualNodeShortHash != null && virtualNodeSchemaRequest.cpiChecksum != null) {
                        val virtualNodeInfo = virtualNodeInfoReadService
                            .getByHoldingIdentityShortHash(
                                ShortHash.parse(
                                    virtualNodeSchemaRequest.virtualNodeShortHash
                                )
                            )
                            ?: throw ResourceNotFoundException(
                                "Virtual node",
                                virtualNodeSchemaRequest.virtualNodeShortHash
                            )
                        val connectionVNodeVault =
                            dbConnectionManager.createDatasource(virtualNodeInfo.vaultDdlConnectionId!!).connection
                        buildSqlWithStringWriter(
                            connectionVNodeVault,
                            getCpkChangelog(virtualNodeSchemaRequest.cpiChecksum)
                        )
                    } else {
                        throw IllegalArgumentException("Illegal argument combination for virtualNodeSchema")
                    }
                }
                ).toString()

            else -> throw IllegalArgumentException("Cannot use dbType that does not exist")
        }
        respFuture.complete(
            VirtualNodeManagementResponse(
                instant,
                VirtualNodeSchemaResponse(sql)
            )
        )
    }

    private fun getChangelog(dbType: String): DbChange {
        val resourceSubPath = "vnode-$dbType"
        val schemaClass = DbSchema::class.java
        val fullName = "${schemaClass.packageName}.$resourceSubPath"
        val resourcePrefix = fullName.replace('.', '/')
        val changeLogFiles = ClassloaderChangeLog.ChangeLogResourceFiles(
            fullName,
            listOf("$resourcePrefix/db.changelog-master.xml"), // VirtualNodeDbType.VAULT.dbChangeFiles,
            classLoader = schemaClass.classLoader
        )
        return ClassloaderChangeLog(linkedSetOf(changeLogFiles))
    }

    private fun getCpkChangelog(cpiChecksum: String): DbChange {
        val cpkDbChangeLogRepository = CpiCpkRepositoryFactory().createCpkDbChangeLogRepository()
        dbConnectionManager.getClusterEntityManagerFactory().createEntityManager().transaction { em ->
            val cpiMetadata =
                CpiCpkRepositoryFactory().createCpiMetadataRepository().findByFileChecksum(em, cpiChecksum)
            val changelogsPerCpk = cpkDbChangeLogRepository.findByCpiId(em, cpiMetadata!!.cpiId)
            return VirtualNodeDbChangeLog(changelogsPerCpk)
        }
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
