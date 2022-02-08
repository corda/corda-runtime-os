package net.corda.db.testkit

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
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
     * Get a Postgres EntityManager configuration if system properties set as necessary. Otherwise, falls back on
     * in-memory implementation.
     */
    fun getEntityManagerConfiguration(inMemoryDbName: String): EntityManagerConfiguration {
        val port = System.getProperty("postgresPort")
        return if (!port.isNullOrBlank()) {
            val postgresDb = getPropertyNonBlank("postgresDb", "postgres")
            val host = getPropertyNonBlank("postgresHost", "localhost")
            val jdbcUrl = "jdbc:postgresql://$host:$port/$postgresDb"
            logger.info("Using Postgres URL $jdbcUrl".emphasise())
            val ds = PostgresDataSourceFactory().create(
                jdbcUrl,
                getPropertyNonBlank("postgresUser", "postgres"),
                getPropertyNonBlank("postgresPassword", "password")
            )
            DbEntityManagerConfiguration(ds, true, true, DdlManage.NONE)
        } else {
            logger.info("Using in-memory (HSQL) DB".emphasise())
            InMemoryEntityManagerConfiguration(inMemoryDbName)
        }
    }

    fun createConfig(inMemoryDbName: String): Config {
        val port = System.getProperty("postgresPort")
        if(!port.isNullOrBlank()) {
            val postgresDb = getPropertyNonBlank("postgresDb", "postgres")
            val host = getPropertyNonBlank("postgresHost", "localhost")
            return ConfigFactory.empty()
                .withValue(ConfigKeys.JDBC_URL, ConfigValueFactory.fromAnyRef("jdbc:postgresql://$host:$port/$postgresDb"))
                .withValue(ConfigKeys.DB_USER, ConfigValueFactory.fromAnyRef(getPropertyNonBlank("postgresUser", "postgres")))
                .withValue(ConfigKeys.DB_PASS, ConfigValueFactory.fromAnyRef(getPropertyNonBlank("postgresPassword", "password")))
        } else {
            // in memory
            return ConfigFactory.empty()
                .withValue(ConfigKeys.JDBC_DRIVER, ConfigValueFactory.fromAnyRef("org.hsqldb.jdbc.JDBCDriver"))
                .withValue(ConfigKeys.JDBC_URL, ConfigValueFactory.fromAnyRef("jdbc:hsqldb:mem:$inMemoryDbName"))
                .withValue(ConfigKeys.DB_USER, ConfigValueFactory.fromAnyRef("sa"))
                .withValue(ConfigKeys.DB_PASS, ConfigValueFactory.fromAnyRef(""))
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