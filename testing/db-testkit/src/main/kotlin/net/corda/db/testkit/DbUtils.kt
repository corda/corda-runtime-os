package net.corda.db.testkit

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.db.core.DBBaseDataSourceFactory
import net.corda.db.core.CloseableDataSource
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.DdlManage
import net.corda.orm.EntityManagerConfiguration
import net.corda.schema.configuration.DatabaseConfig
import net.corda.test.util.LoggingUtils.emphasise
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Connection

interface DbUtilsHelper {

    fun isInMemory(): Boolean
    fun getDatabase(): String
    fun getAdminUser(): String
    fun getAdminPassword(): String

    @Suppress("LongParameterList")
    fun getEntityManagerConfiguration(
        inMemoryDbName : String = "",
        dbUser: String? = null,
        dbPassword: String? = null,
        schemaName: String? = null,
        createSchema: Boolean = false,
        showSql: Boolean = true,
        rewriteBatchedInserts: Boolean = false
    ): EntityManagerConfiguration
    fun createDataSource(
        dbUser:String? = null,
        dbPassword: String? = null,
        schemaName: String? = null,
        createSchema: Boolean = false,
        rewriteBatchedInserts: Boolean = false
    ): CloseableDataSource
    fun createConfig(
        inMemoryDbName: String = "",
        dbUser: String? = null,
        dbPassword: String? = null,
        schemaName: String? = null
    ): Config
}

class PostgresHelper : DbUtilsHelper{
    companion object {
        private const val POSTGRES_PORT_PROPERTY = "postgresPort"
        private const val POSTGRES_HOST_PROPERTY = "postgresHost"
    }

    override fun isInMemory() = false

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override fun getDatabase() = getPropertyNonBlank("postgresDb","postgres")

    override fun getAdminUser() = if (isInMemory()) "sa" else getPropertyNonBlank("postgresUser","postgres")

    override fun getAdminPassword() = if (isInMemory()) "" else getPropertyNonBlank("postgresPassword", "password")

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
        return DbEntityManagerConfiguration(ds,showSql,true,DdlManage.NONE)

    }

    override fun createDataSource(
        dbUser: String?,
        dbPassword: String?,
        schemaName: String?,
        createSchema: Boolean,
        rewriteBatchedInserts: Boolean
    ): CloseableDataSource {
        val port = System.getProperty(POSTGRES_PORT_PROPERTY)
        val postgresDb = getDatabase()
        val host = getPropertyNonBlank(POSTGRES_HOST_PROPERTY,"localhost")
        var jdbcUrl = "jdbc:postgresql://$host:$port/$postgresDb"

        val factory = DBBaseDataSourceFactory()

        val user = dbUser ?: getAdminUser()
        val password = dbPassword ?: getAdminPassword()

        if (!schemaName.isNullOrBlank()) {
            if (createSchema) {
                logger.info("Creating schema: $schemaName".emphasise())
                factory.create("org.postgresql.Driver",jdbcUrl, user, password, maximumPoolSize = 1).connection.createSchema(schemaName)
            }
            jdbcUrl = if (rewriteBatchedInserts) {
                "$jdbcUrl?currentSchema=$schemaName&reWriteBatchedInserts=true"
            } else {
                "$jdbcUrl?currentSchema=$schemaName"
            }
        }
        logger.info("Using Postgres URL $jdbcUrl".emphasise())
        return factory.create("org.postgresql.Driver",jdbcUrl, user, password)
    }

    override fun createConfig(
        inMemoryDbName: String,
        dbUser: String?,
        dbPassword: String?,
        schemaName: String?
    ): Config {
        val port = System.getProperty(POSTGRES_PORT_PROPERTY)
        val user = dbUser ?: getAdminUser()
        val password = dbPassword ?: getAdminPassword()
        val postgresDb = getDatabase()
        val host = getPropertyNonBlank(POSTGRES_HOST_PROPERTY, "localhost")
        var jdbcUrl = "jdbc:postgresql://$host:$port/$postgresDb"
        if (!schemaName.isNullOrBlank()) {
            jdbcUrl = "$jdbcUrl?currentSchema=$schemaName"
        }
        return ConfigFactory.empty()
            .withValue(DatabaseConfig.JDBC_URL, ConfigValueFactory.fromAnyRef(jdbcUrl))
            .withValue(DatabaseConfig.DB_USER, ConfigValueFactory.fromAnyRef(user))
            .withValue(DatabaseConfig.DB_PASS, ConfigValueFactory.fromAnyRef(password))
    }
}

class SQLServerHelper : DbUtilsHelper {
    companion object {
        private const val MSSQL_HOST_PROPERTY = "mssqlHost"
        private const val MSSQL_PORT_PROPERTY = "mssqlPort"
    }

    override fun isInMemory() = false

    override fun getDatabase() = getPropertyNonBlank("sqlDb", "sql")

    override fun getAdminUser() = if (isInMemory()) "sa" else getPropertyNonBlank("mssqlUser", "sa")

    override fun getAdminPassword() = if (isInMemory()) "" else getPropertyNonBlank("mssqlPassword", "password")

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
        if (!port.isNullOrBlank()) {
            val host = getPropertyNonBlank(MSSQL_HOST_PROPERTY, "localhost")
            var jdbcUrl = "jdbc:sqlserver://$host:$port;encrypt=true;trustServerCertificate=true;"
            if (!schemaName.isNullOrBlank()) {
                jdbcUrl = "$jdbcUrl?currentSchema=$schemaName"
            }
            return ConfigFactory.empty()
                .withValue(DatabaseConfig.JDBC_URL, ConfigValueFactory.fromAnyRef(jdbcUrl))
                .withValue(DatabaseConfig.DB_USER, ConfigValueFactory.fromAnyRef(user))
                .withValue(DatabaseConfig.DB_PASS, ConfigValueFactory.fromAnyRef(password))
        } else {
            return ConfigFactory.empty()
                .withValue(DatabaseConfig.JDBC_DRIVER, ConfigValueFactory.fromAnyRef("org.hsqldb.jdbc.JDBCDriver"))
                .withValue(DatabaseConfig.JDBC_URL, ConfigValueFactory.fromAnyRef("jdbc:hsqldb:mem:$inMemoryDbName"))
                .withValue(DatabaseConfig.DB_USER, ConfigValueFactory.fromAnyRef(user))
                .withValue(DatabaseConfig.DB_PASS, ConfigValueFactory.fromAnyRef(password))
        }
    }
}

class HSQLHelper : DbUtilsHelper {
    override fun isInMemory(): Boolean = true

    override fun getDatabase(): String = ""

    override fun getAdminUser() = if (isInMemory()) "sa" else getPropertyNonBlank("postgresUser","postgres")

    override fun getAdminPassword() = if (isInMemory()) "" else getPropertyNonBlank("postgresPassword", "password")

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
        logger.info("Using in-memory (HSQL) DB".emphasise())
        return TestInMemoryEntityManagerConfiguration(inMemoryDbName, showSql).also {
            if (createSchema) {
                it.dataSource.connection.createSchema(schemaName)
            }
        }
    }

    override fun createDataSource(
        dbUser: String?,
        dbPassword: String?,
        schemaName: String?,
        createSchema: Boolean,
        rewriteBatchedInserts: Boolean
    ): CloseableDataSource {
        val factory = DBBaseDataSourceFactory()
        val user = dbUser ?: getAdminUser()
        val password = dbPassword ?: getAdminPassword()
        return factory.create("org.hsqldb.jdbc.JDBCDriver","", user, password)
    }

    override fun createConfig(
        inMemoryDbName: String,
        dbUser: String?,
        dbPassword: String?,
        schemaName: String?
    ): Config {
        val user = dbUser ?: getAdminUser()
        val password = dbPassword ?: getAdminPassword()

        return ConfigFactory.empty()
            .withValue(DatabaseConfig.JDBC_DRIVER, ConfigValueFactory.fromAnyRef("org.hsqldb.jdbc.JDBCDriver"))
            .withValue(DatabaseConfig.JDBC_URL, ConfigValueFactory.fromAnyRef("jdbc:hsqldb:mem:$inMemoryDbName"))
            .withValue(DatabaseConfig.DB_USER, ConfigValueFactory.fromAnyRef(user))
            .withValue(DatabaseConfig.DB_PASS, ConfigValueFactory.fromAnyRef(password))
    }

}

object DbUtils {

    val isNotPostgres = System.getProperty("postgresPort").isNullOrBlank()
    val isNotMSSQL = System.getProperty("mssqlPort").isNullOrBlank()

    var utilsHelper: DbUtilsHelper = when {
        !isNotMSSQL -> SQLServerHelper()
        !isNotPostgres -> PostgresHelper()
        else -> {
            HSQLHelper()
        }
    }

    val isInMemory = utilsHelper.isInMemory()

    fun getDatabase() = utilsHelper.getDatabase()

    @Suppress("LongParameterList")
    fun getEntityManagerConfiguration(
        inMemoryDbName: String = "",
        dbUser: String? = null,
        dbPassword: String? = null,
        schemaName: String? = null,
        createSchema: Boolean = false,
        showSql: Boolean = true,
        rewriteBatchedInserts: Boolean = false
    ): EntityManagerConfiguration = utilsHelper.getEntityManagerConfiguration(
        inMemoryDbName, dbUser, dbPassword, schemaName, createSchema, showSql, rewriteBatchedInserts
    )

    fun createDataSource(
        dbUser: String? = null,
        dbPassword: String? = null,
        schemaName: String? = null,
        createSchema: Boolean = false,
        rewriteBatchedInserts: Boolean = false
    ): CloseableDataSource = utilsHelper.createDataSource(
        dbUser, dbPassword, schemaName, createSchema, rewriteBatchedInserts
    )

    fun createConfig(
        inMemoryDbName: String = "",
        dbUser: String? = null,
        dbPassword: String? = null,
        schemaName: String? = null
    ): Config = utilsHelper.createConfig(
        inMemoryDbName, dbUser, dbPassword, schemaName
    )
}

private fun getPropertyNonBlank(key: String, defaultValue: String): String {
    val value = System.getProperty(key)
    return if (value.isNullOrBlank()) {
        defaultValue
    } else {
        value
    }
}
private fun Connection.createSchema(schemaName: String?) {
    requireNotNull(schemaName)
    this.use { conn ->
        conn.prepareStatement("CREATE SCHEMA IF NOT EXISTS $schemaName;").execute()
        conn.commit()
    }
}