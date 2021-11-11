package net.corda.db.testkit

import net.corda.db.core.PostgresDataSourceFactory
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.DdlManage
import net.corda.orm.EntityManagerConfiguration
import net.corda.test.util.LoggingUtils.emphasise
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object DbUtils {

    private val logger: Logger = LoggerFactory.getLogger("DbUtils")

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

    private fun getPropertyNonBlank(key: String, defaultValue: String): String {
        val value = System.getProperty(key)
        return if (value.isNullOrBlank()) {
            defaultValue
        } else {
            value
        }
    }
}