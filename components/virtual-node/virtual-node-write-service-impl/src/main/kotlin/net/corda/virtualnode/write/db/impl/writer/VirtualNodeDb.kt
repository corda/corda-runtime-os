package net.corda.virtualnode.write.db.impl.writer

import net.corda.db.admin.DbChange
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.connection.manager.DBConfigurationException
import net.corda.db.connection.manager.DbAdmin
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.DbPrivilege
import net.corda.db.core.DbPrivilege.DDL
import net.corda.db.core.DbPrivilege.DML
import net.corda.db.schema.DbSchema
import net.corda.v5.base.util.contextLogger

/**
 * Encapsulates access to a single database, either of type VAULT or CONFIG, at muliple different provilege levels
 * DDL and DML. Handles setting up schemas and users plus applying migrations.
 *
 * This is a helper class for VirtualNodeWrierProcessor which simply encapsulates database access.
 *
 * Unit test coverage is only via the test cases in VirtualNodeWrriterProcessorTests.
 */
@Suppress("LongParameterList")
class VirtualNodeDb(
    private val dbType: VirtualNodeDbType, val isClusterDb: Boolean, private val holdingIdentityShortHash: String,
    val dbConnections: Map<DbPrivilege, DbConnection?>, private val dbAdmin: DbAdmin,
    private val dbConnectionManager: DbConnectionManager, private val schemaMigrator: LiquibaseSchemaMigrator
) {

    companion object {
        private val log = contextLogger()
    }

    /**
     * Creates DB schema and user
     */
    @Suppress("NestedBlockDepth")
    fun createSchemasAndUsers() {
        if (isClusterDb) {
            // Order is important because DB schema should be deleted first if DDL user already exists
            for (privilege in listOf(DDL, DML)) {
                dbConnections[privilege]!!.let { connection ->
                    val user = connection.getUser() ?:
                        throw DBConfigurationException("DB user not known for connection ${connection.description}")
                    val password = connection.getPassword() ?:
                        throw DBConfigurationException("DB password not known for connection ${connection.description}")
                    val dbSchema = dbType.getSchemaName(holdingIdentityShortHash)
                    // This covers scenario when previous virtual node on-boarding request failed after user was created
                    // Since connections are persisted at later point, user's password is lost, so user is re-created
                    if (dbAdmin.userExists(user)) {
                        if (privilege == DDL) {
                            log.info("User for connection ${connection.description} already exists in DB, schema will be deleted")
                            dbAdmin.deleteSchema(dbSchema)
                        }
                        log.info("User for connection ${connection.description} already exists in DB, it will be re-created")
                        dbAdmin.deleteUser(user)
                    }
                    // When DML user is created, it is granted with privileges related to DB objects created by DDL user
                    // (therefore DDL user has to be provided as grantee)
                    val grantee = if (privilege == DML) dbConnections[DDL]!!.getUser() else null
                    dbAdmin.createDbAndUser(dbSchema, user, password, privilege, grantee)
                }
            }
        }
    }

    /**
     * Runs DB migration
     */
    fun runDbMigration() {
        val dbConnection = dbConnections[DDL]
        if (dbConnection == null) throw VirtualNodeDbException("No DDL database connection when due to apply system migrations")
        dbConnectionManager.getDataSource(dbConnection.config).use { dataSource ->
            val dbChangeFiles = dbType.dbChangeFiles
            val changeLogResourceFiles = setOf(DbSchema::class.java).mapTo(LinkedHashSet()) { klass ->
                ClassloaderChangeLog.ChangeLogResourceFiles(klass.packageName, dbChangeFiles, klass.classLoader)
            }
            val dbChange = ClassloaderChangeLog(changeLogResourceFiles)
            val dbSchema = dbType.getSchemaName(holdingIdentityShortHash)

            dataSource.connection.use { connection ->
                schemaMigrator.updateDb(connection, dbChange, dbSchema)
            }
        }
    }

    fun runCpiMigrations(dbChange: DbChange) {
        val dbConnection = dbConnections[DDL]
            ?: throw VirtualNodeDbException("No DDL database connection when due to apply CPI migrations")
        dbConnectionManager.getDataSource(dbConnection.config).use { dataSource ->
            dataSource.connection.use { connection ->
                LiquibaseSchemaMigratorImpl().updateDb(connection, dbChange)
            }
        }
    }

}
