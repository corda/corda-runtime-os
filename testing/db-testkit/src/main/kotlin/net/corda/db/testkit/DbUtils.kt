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
        val postgresDb = System.getProperty("postgresDb")
        return if (!postgresDb.isNullOrBlank()) {
            logger.info("Using Postgres on port ${System.getProperty("postgresPort")}".emphasise())
            val jdbcUrl =
                "jdbc:postgresql://${System.getProperty("postgresHost")}:${System.getProperty("postgresPort")}/$postgresDb"
            val ds = PostgresDataSourceFactory().create(
                jdbcUrl,
                System.getProperty("postgresUser"),
                System.getProperty("postgresPassword")
            )
            DbEntityManagerConfiguration(ds, true, true, DdlManage.UPDATE)
        } else {
            logger.info("Using in-memory (HSQL) DB".emphasise())
            InMemoryEntityManagerConfiguration(inMemoryDbName)
        }
    }
}