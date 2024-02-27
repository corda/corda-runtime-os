package net.corda.virtualnode.write.db.impl.writer

import net.corda.db.admin.DbChange
import net.corda.db.connection.manager.VirtualNodeDbType
import net.corda.db.core.DbPrivilege

/**
 * Represents a Virtual Node Database
 *
 * @property isPlatformManagedDb true if the database objects are managed by the Corda platform
 * @property ddlConnectionProvided true if a DDL connection string has been provided
 * @property dbConnections Map of [DbPrivilege] type to its associated connection config.
 * @property dbType DB type (usage)
 */
internal interface VirtualNodeDb {
    val isPlatformManagedDb: Boolean
    val ddlConnectionProvided: Boolean
    val dbConnections: Map<DbPrivilege, DbConnection?>
    val dbType: VirtualNodeDbType

    /**
     * Creates DB schema and user
     */
    @Suppress("NestedBlockDepth")
    fun createSchemasAndUsers()

    /**
     * runDBMigration
     *
     * @param migrationTagToApply [string?] is an optional tag to be added to the liquibase migration.
     *  See: https://docs.liquibase.com/change-types/tag-database.html
     */
    fun runDbMigration(migrationTagToApply: String?)

    /**
     * runCpiMigrations: runs a changeset represented as a [DbChange], with the [migrationTagToApply] tagged to each
     *  change within that changeset.
     *
     * These migrations come from the CPI and so are user created.
     *
     * @param dbChange
     * @param migrationTagToApply
     */
    fun runCpiMigrations(dbChange: DbChange, migrationTagToApply: String)

    /**
     * Returns true if all DB migrations have been applied, false otherwise.
     *
     * Checks the platform DB schema matching the dbType field.
     */
    fun checkDbMigrationsArePresent(): Boolean

    /**
     * Returns true if all DB migrations have been applied, false otherwise.
     *
     * @param dbChange holds the DB schema resources read from the CPI
     */
    fun checkCpiMigrationsArePresent(dbChange: DbChange): Boolean
}
