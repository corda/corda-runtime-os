package net.corda.virtualnode.write.db.impl.writer

import net.corda.db.admin.DbChange
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.connection.manager.DBConfigurationException
import net.corda.db.connection.manager.DbAdmin
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.VirtualNodeDbType
import net.corda.db.core.DbPrivilege
import net.corda.db.core.DbPrivilege.DDL
import net.corda.db.core.DbPrivilege.DML
import net.corda.db.schema.DbSchema
import net.corda.virtualnode.ShortHash
import org.slf4j.LoggerFactory

/**
 * Encapsulates access to a single database, either of type VAULT or CONFIG, at multiple different privilege levels
 * DDL and DML. Handles setting up schemas and users plus applying migrations.
 *
 * This is a helper class for VirtualNodeWriterProcessor which simply encapsulates database access.
 */
@Suppress("LongParameterList")
internal class VirtualNodeDbImpl(
    override val isPlatformManagedDb: Boolean,
    override val dbConnections: Map<DbPrivilege, DbConnection?>,
    override val dbType: VirtualNodeDbType,
    private val holdingIdentityShortHash: ShortHash,
    private val dbAdmin: DbAdmin,
    private val dbConnectionManager: DbConnectionManager,
    private val schemaMigrator: LiquibaseSchemaMigrator
) : VirtualNodeDb {

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    /**
     * Creates DB schema and user
     */
    @Suppress("NestedBlockDepth")
    override fun createSchemasAndUsers() {
        if (isPlatformManagedDb) {
            // Order is important because DB schema should be deleted first if DDL user already exists
            for (privilege in listOf(DDL, DML)) {
                dbConnections[privilege]!!.let { connection ->
                    val user = connection.getUser()
                        ?: throw DBConfigurationException("DB user not known for connection ${connection.description}")
                    val password = connection.getPassword()
                        ?: throw DBConfigurationException("DB password not known for connection ${connection.description}")
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
     * runDBMigration
     *
     * @param migrationTagToApply [string?] is an optional tag to be added to the liquibase migration.
     *  See: https://docs.liquibase.com/change-types/tag-database.html
     */
    override fun runDbMigration(migrationTagToApply: String?) {
        val dbConnection = dbConnections[DDL]
            ?: throw VirtualNodeDbException("No DDL database connection when due to apply system migrations")
        dbConnectionManager.getDataSource(dbConnection.config).use { dataSource ->
            val dbChangeFiles = dbType.dbChangeFiles
            val changeLogResourceFiles = setOf(DbSchema::class.java).mapTo(LinkedHashSet()) { klass ->
                ClassloaderChangeLog.ChangeLogResourceFiles(klass.packageName, dbChangeFiles, klass.classLoader)
            }
            val dbChange = ClassloaderChangeLog(changeLogResourceFiles)

            dataSource.connection.use { connection ->
                if (isPlatformManagedDb) {
                    val dbSchema = dbType.getSchemaName(holdingIdentityShortHash)
                    schemaMigrator.updateDb(connection, dbChange, dbSchema, migrationTagToApply)
                } else {
                    schemaMigrator.updateDb(connection, dbChange, migrationTagToApply)
                }
            }
        }
    }

    /**
     * runCpiMigrations: runs a changeset represented as a [DbChange], with the [migrationTagToApply] tagged to each
     *  change within that changeset.
     *
     * These migrations come from the CPI and so are user created.
     *
     * @param dbChange
     * @param migrationTagToApply
     */
    override fun runCpiMigrations(dbChange: DbChange, migrationTagToApply: String) {
        val dbConnection = dbConnections[DDL]
            ?: throw VirtualNodeDbException("No DDL database connection when due to apply CPI migrations")
        dbConnectionManager.getDataSource(dbConnection.config).use { dataSource ->
            dataSource.connection.use { connection ->
                schemaMigrator.updateDb(connection, dbChange, tag = migrationTagToApply)
            }
        }
    }
}
