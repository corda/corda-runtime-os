package net.corda.virtualnode.write.db.impl.writer

import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.connection.manager.DbAdmin
import net.corda.db.connection.manager.DbConnectionsRepository
import net.corda.db.core.DbPrivilege
import net.corda.db.core.DbPrivilege.*
import net.corda.db.schema.DbSchema
import java.util.*

/**
 * Stores virtual node database connections and perform database operations.
 */
class VirtualNodeDb(
    private val dbType: VirtualNodeDbType, val isClusterDb: Boolean, private val holdingIdentityId: String,
    val dbConnections: Map<DbPrivilege, DbConnection?>, private val dbAdmin: DbAdmin, private val adminJdbcUrl: String,
    private val dbConnectionRepository: DbConnectionsRepository, private val schemaMigrator: LiquibaseSchemaMigrator
) {

    /**
     * Creates DB schema and user
     */
    fun createSchemasAndUsers() {
        dbConnections.forEach { (privilege, connection) ->
            connection?.user?.let { user ->
                connection.password?.let { password ->
                    dbAdmin.createDbAndUser(dbType.getSchemaName(holdingIdentityId), user, password, adminJdbcUrl, privilege)
                }
            }
        }
    }

    /**
     * Runs DB migration
     */
    fun runDbMigration() {
        dbConnections[DDL]?.let { dbConnection ->
            dbConnectionRepository.get(dbConnection.name, dbConnection.privilege)?.let { dataSource->
                val dbChangeFiles = dbType.dbChangeFiles
                val changeLogResourceFiles = setOf(DbSchema::class.java).mapTo(LinkedHashSet()) { klass ->
                    ClassloaderChangeLog.ChangeLogResourceFiles(klass.packageName, dbChangeFiles, klass.classLoader)
                }
                val dbChange = ClassloaderChangeLog(changeLogResourceFiles)
                val dbSchema = dbType.getSchemaName(holdingIdentityId)

                dataSource.connection.use { connection ->
                    schemaMigrator.updateDb(connection, dbChange, dbSchema)
                }
            }
        }
    }
}