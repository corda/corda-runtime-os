package net.corda.db.testkit.dbutilsimpl

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.db.core.CloseableDataSource
import net.corda.db.core.createDataSource
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.DdlManage
import net.corda.orm.EntityManagerConfiguration
import net.corda.schema.configuration.DatabaseConfig
import net.corda.test.util.LoggingUtils.emphasise
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Connection

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
    abstract override fun isInMemory(): Boolean

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    abstract override fun getDatabase(): String

    abstract override fun getAdminUser(): String

    abstract override fun getAdminPassword(): String

    abstract val port: String

    abstract val host: String

    abstract var jdbcUrl: String

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
        val ds = createDataSource(dbUser,dbPassword, schemaName, createSchema, rewriteBatchedInserts)
        return DbEntityManagerConfiguration(ds,showSql,true, DdlManage.NONE)
    }

    abstract fun createSchema(connection: Connection, schemaName: String): Pair<String, String>

    override fun createDataSource(
        dbUser: String?,
        dbPassword: String?,
        schemaName: String?,
        createSchema: Boolean,
        rewriteBatchedInserts: Boolean
    ): CloseableDataSource {
        val user = dbUser ?: getAdminUser()
        val password = dbPassword ?: getAdminPassword()

        if (!schemaName.isNullOrBlank()) {
            val credentials = if (createSchema) {
                logger.info("Creating schema: $schemaName".emphasise())
                createSchema( createDataSource(driverClass,jdbcUrl,user,password, maximumPoolSize = 1).connection, schemaName)
            } else {
                user to password
            }
            val jdbcUrlCopy = if (rewriteBatchedInserts) {
                "$jdbcUrl?currentSchema=$schemaName&reWriteBatchedInserts=true"
            } else {
                "$jdbcUrl?currentSchema=$schemaName"
            }
            logger.info("Using URL $jdbcUrlCopy".emphasise())
            return createDataSource(driverClass,jdbcUrlCopy, credentials.first, credentials.second)
        }
        logger.info("Using URL $jdbcUrl".emphasise())
        return createDataSource(driverClass,jdbcUrl, user, password)
    }

    override fun createConfig(
        inMemoryDbName: String,
        dbUser: String?,
        dbPassword: String?,
        schemaName: String?
    ): Config {
        val user = dbUser ?: getAdminUser()
        val password = dbPassword ?: getAdminPassword()
        val currentJdbcUrl = if (!schemaName.isNullOrBlank()) {
            "$jdbcUrl?currentSchema=$schemaName"
        } else {
            jdbcUrl
        }
        return ConfigFactory.empty()
            .withValue(DatabaseConfig.JDBC_URL, ConfigValueFactory.fromAnyRef(currentJdbcUrl))
            .withValue(DatabaseConfig.DB_USER, ConfigValueFactory.fromAnyRef(user))
            .withValue(DatabaseConfig.DB_PASS, ConfigValueFactory.fromAnyRef(password))
    }

}