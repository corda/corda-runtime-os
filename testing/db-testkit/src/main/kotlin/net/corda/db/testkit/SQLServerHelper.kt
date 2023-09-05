package net.corda.db.testkit

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.db.core.CloseableDataSource
import net.corda.db.core.DBBaseDataSourceFactory
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.DdlManage
import net.corda.orm.EntityManagerConfiguration
import net.corda.schema.configuration.DatabaseConfig
import net.corda.test.util.LoggingUtils.emphasise
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class SQLServerHelper : DbUtilsHelper {
    companion object {
        private const val MSSQL_HOST_PROPERTY = "mssqlHost"
        private const val MSSQL_PORT_PROPERTY = "mssqlPort"
    }

    override fun isInMemory() = false

    override fun getDatabase() = getPropertyNonBlank("sqlDb", "sql")

    override fun getAdminUser() = getPropertyNonBlank("mssqlUser", "sa")

    override fun getAdminPassword() = getPropertyNonBlank("mssqlPassword", "password")

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

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
        return (DbEntityManagerConfiguration(ds, showSql, true, DdlManage.NONE))
    }

    override fun createDataSource(
        dbUser: String?,
        dbPassword: String?,
        schemaName: String?,
        createSchema: Boolean,
        rewriteBatchedInserts: Boolean
    ): CloseableDataSource {
        val port = System.getProperty(MSSQL_PORT_PROPERTY)
        val host = getPropertyNonBlank(MSSQL_HOST_PROPERTY, "localhost")
        var jdbcUrl = "jdbc:sqlserver://$host:$port;encrypt=true;trustServerCertificate=true;"

        val factory = DBBaseDataSourceFactory()

        val user = dbUser ?: getAdminUser()
        val password = dbPassword ?: getAdminPassword()

        if (!schemaName.isNullOrBlank()) {
            if (createSchema) {
                logger.info("Creating schema: $schemaName".emphasise())
                factory.create("com.microsoft.sqlserver.jdbc.SQLServerDriver", jdbcUrl, user, password, maximumPoolSize = 1)
                    .connection.createSchema(schemaName)
            }
            jdbcUrl = if (rewriteBatchedInserts) {
                "$jdbcUrl?currentSchema=$schemaName&reWriteBatchedInserts=true"
            } else {
                "$jdbcUrl?currentSchema=$schemaName"
            }
        }
        logger.info("Using SQL Server URL $jdbcUrl".emphasise())
        return factory.create("com.microsoft.sqlserver.jdbc.SQLServerDriver",jdbcUrl, user, password)
    }

    override fun createConfig(
        inMemoryDbName: String,
        dbUser: String?,
        dbPassword: String?,
        schemaName: String?
    ): Config {
        val port = System.getProperty(MSSQL_PORT_PROPERTY)
        val user = dbUser ?: getAdminUser()
        val password = dbPassword ?: getAdminPassword()
        val host = getPropertyNonBlank(MSSQL_HOST_PROPERTY, "localhost")
        var jdbcUrl = "jdbc:sqlserver://$host:$port;encrypt=true;trustServerCertificate=true;"
        if (!schemaName.isNullOrBlank()) {
            jdbcUrl = "$jdbcUrl?currentSchema=$schemaName"
        }
        return ConfigFactory.empty()
            .withValue(DatabaseConfig.JDBC_URL, ConfigValueFactory.fromAnyRef(jdbcUrl))
            .withValue(DatabaseConfig.DB_USER, ConfigValueFactory.fromAnyRef(user))
            .withValue(DatabaseConfig.DB_PASS, ConfigValueFactory.fromAnyRef(password))
    }
}