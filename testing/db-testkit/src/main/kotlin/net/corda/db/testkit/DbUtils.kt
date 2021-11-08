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

    fun getEntityManagerConfiguration(): EntityManagerConfiguration? {
        return if (!System.getProperty("postgresDb").isNullOrBlank()) {
            logger.info("Using Postgres on port ${System.getProperty("postgresPort")}".emphasise())
            val jdbcUrl =
                "jdbc:postgresql://${System.getProperty("postgresHost")}:${System.getProperty("postgresPort")}/" +
                        System.getProperty("postgresDb")
            val ds = PostgresDataSourceFactory().create(
                jdbcUrl,
                System.getProperty("postgresUser"),
                System.getProperty("postgresPassword")
            )
            DbEntityManagerConfiguration(ds, true, true, DdlManage.UPDATE)
        } else {
            null
        }
    }
}