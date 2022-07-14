package net.corda.db.testkit

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.db.core.CloseableDataSource
import net.corda.db.core.PostgresDataSourceFactory
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.DdlManage
import net.corda.orm.EntityManagerConfiguration
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.LoggingUtils.emphasise
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object DbUtils {

    private val logger: Logger = LoggerFactory.getLogger("DbUtils")

    val isInMemory = System.getProperty("postgresPort").isNullOrBlank()

    /**
     * Returns Postgres DB name
     */
    fun getPostgresDatabase() = getPropertyNonBlank("postgresDb", "postgres")

    /**
     * Returns DB admin user
     */
    fun getAdminUser() = if (isInMemory) "sa" else getPropertyNonBlank("postgresUser", "postgres")

    /**
     * Returns DB admin user's password
     */
    fun getAdminPassword() = if (isInMemory) "" else getPropertyNonBlank("postgresPassword", "password")

    /**
     * Get a Postgres EntityManager configuration if system properties set as necessary. Otherwise, falls back on
     * in-memory implementation.
     */
    fun getEntityManagerConfiguration(
        inMemoryDbName: String,
        dbUser:String? = null,
        dbPassword: String? = null,
        schemaName: String? = null,
        createSchema: Boolean = false
    ): EntityManagerConfiguration {
        val port = System.getProperty("postgresPort")
        return if (!port.isNullOrBlank()) {
            val ds = createPostgresDataSource(dbUser, dbPassword, schemaName, createSchema)
            DbEntityManagerConfiguration(ds, true, true, DdlManage.NONE)
        } else {
            logger.info("Using in-memory (HSQL) DB".emphasise())
            TestInMemoryEntityManagerConfiguration(inMemoryDbName).also {
                if(createSchema) {
                    it.dataSource.connection.use { conn ->
                        conn.prepareStatement("CREATE SCHEMA IF NOT EXISTS $schemaName;").execute()
                    }
                }
            }
        }
    }

    /**
     * Creates Postgres [CloseableDataSource]
     *
     * @param dbUser DB user. If value is not provided, value of the system property "postgresUser" is used.
     *               If system property is not set then value "postgress" is used.
     * @param dbPassword DB password. If value is not provided, value of the system property "postgresPassword" is used.
     *                   If system property is not set then value "password" is used
     */
    fun createPostgresDataSource(
        dbUser:String? = null,
        dbPassword: String? = null,
        schemaName: String? = null,
        createSchema: Boolean = false
    ): CloseableDataSource {
        val port = System.getProperty("postgresPort")
        val postgresDb = getPostgresDatabase()
        val host = getPropertyNonBlank("postgresHost", "localhost")
        var jdbcUrl = "jdbc:postgresql://$host:$port/$postgresDb"
        val factory = PostgresDataSourceFactory()
        val user = dbUser ?: getAdminUser()
        val password = dbPassword ?: getAdminPassword()
        if(!schemaName.isNullOrBlank()) {
            if (createSchema) {
                logger.info("Creating schema: $schemaName".emphasise())
                factory.create(jdbcUrl, user, password, maximumPoolSize = 1).connection.use { conn ->
                    conn.prepareStatement("CREATE SCHEMA IF NOT EXISTS $schemaName;").execute()
                }
            }
            jdbcUrl = "$jdbcUrl?currentSchema=$schemaName"
        }
        logger.info("Using Postgres URL $jdbcUrl".emphasise())
        // reduce poolsize when testing
        return factory.create(jdbcUrl, user, password, maximumPoolSize = 5)
    }

    fun createConfig(
        inMemoryDbName: String,
        dbUser:String? = null,
        dbPassword: String? = null,
        schemaName: String? = null
    ): Config {
        val port = System.getProperty("postgresPort")
        val user = dbUser ?: getAdminUser()
        val password = dbPassword ?: getAdminPassword()
        if(!port.isNullOrBlank()) {
            val postgresDb = getPostgresDatabase()
            val host = getPropertyNonBlank("postgresHost", "localhost")
            var jdbcUrl = "jdbc:postgresql://$host:$port/$postgresDb"
            if(!schemaName.isNullOrBlank()) {
                jdbcUrl = "$jdbcUrl?currentSchema=$schemaName"
            }
            return ConfigFactory.empty()
                .withValue(ConfigKeys.JDBC_URL, ConfigValueFactory.fromAnyRef(jdbcUrl))
                .withValue(ConfigKeys.DB_USER, ConfigValueFactory.fromAnyRef(user))
                .withValue(ConfigKeys.DB_PASS, ConfigValueFactory.fromAnyRef(password))
        } else {
            // in memory
            return ConfigFactory.empty()
                .withValue(ConfigKeys.JDBC_DRIVER, ConfigValueFactory.fromAnyRef("org.hsqldb.jdbc.JDBCDriver"))
                .withValue(ConfigKeys.JDBC_URL, ConfigValueFactory.fromAnyRef("jdbc:hsqldb:mem:$inMemoryDbName"))
                .withValue(ConfigKeys.DB_USER, ConfigValueFactory.fromAnyRef(user))
                .withValue(ConfigKeys.DB_PASS, ConfigValueFactory.fromAnyRef(password))
        }
    }

    private fun getPropertyNonBlank(key: String, defaultValue: String): String {
        val value = System.getProperty(key)
        return if (value.isNullOrBlank()) {
            defaultValue
        } else {
            value
        }
    }
}