package net.corda.db.testkit.dbutilsimpl

import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.DdlManage
import net.corda.orm.EntityManagerConfiguration

/**
 * An abstract class that provides common functionality for working with databases using JDBC.
 *
 * This class defines abstract methods for retrieving database-related information such as database name,
 * admin user, admin password, port, host, JDBC URL, and driver class. It also provides implementations
 * for creating a data source, configuring an entity manager, and creating a configuration for the database.
 *
 * The reason for this abstract class is to avoid a lot of code duplication that would've happened in the
 * PostgresHelper and SQLServerHelper classes
 *
 * @property port The port on which the database server is running.
 * @property host The hostname of the database server.
 * @property jdbcUrl The JDBC URL used to connect to the database.
 * @property driverClass The name of the JDBC driver to be used.
 */
abstract class AbstractDBHelper : DbUtilsHelper {

    companion object {
        const val DBPORT_PROPERTY = "databasePort"
        const val DBHOST_PROPERTY = "databaseHost"
        const val DBNAME_PROPERTY = "databaseName"
        const val DB_ADMIN_USER_PROPERTY = "databaseAdminUser"
        const val DB_ADMIN_PASSWORD_PROPERTY = "databaseAdminPassword"
    }
    abstract override fun isInMemory(): Boolean

    abstract override fun getDatabase(): String

    abstract override fun getAdminUser(): String

    abstract override fun getAdminPassword(): String

    abstract val port: String

    val host: String = getPropertyNonBlank(DBHOST_PROPERTY, "localhost")

    abstract val jdbcUrl: String

    abstract val driverClass: String

    override fun getEntityManagerConfiguration(
        inMemoryDbName: String,
        dbUser: String?,
        dbPassword: String?,
        schemaName: String?,
        createSchema: Boolean,
        showSql: Boolean,
        rewriteBatchedInserts: Boolean
    ): EntityManagerConfiguration {
        val ds = createDataSource(dbUser, dbPassword, schemaName, createSchema, rewriteBatchedInserts)
        return DbEntityManagerConfiguration(ds, showSql, true, DdlManage.NONE)
    }
}