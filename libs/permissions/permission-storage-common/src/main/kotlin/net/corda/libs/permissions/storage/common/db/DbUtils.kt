package net.corda.libs.permissions.storage.common.db

import net.corda.db.core.PostgresDataSourceFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.storage.common.ConfigKeys.DB_PASSWORD
import net.corda.libs.permissions.storage.common.ConfigKeys.DB_URL
import net.corda.libs.permissions.storage.common.ConfigKeys.DB_USER
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntitiesSet
import net.corda.orm.EntityManagerFactoryFactory
import org.slf4j.LoggerFactory
import javax.persistence.EntityManagerFactory

object DbUtils {

    private val log = LoggerFactory.getLogger(DbUtils::class.java)

    fun obtainEntityManagerFactory(
        dbConfig: SmartConfig, entityManagerFactoryFactory: EntityManagerFactoryFactory,
        entitiesSet: EntitiesSet
    ): EntityManagerFactory {

        val jdbcUrl = dbConfig.getString(DB_URL)
        val username = dbConfig.getString(DB_USER)

        log.info("Creating EntityManagerFactory for '$jdbcUrl' for user: '$username'")

        val dbSource = PostgresDataSourceFactory().create(
            jdbcUrl,
            username,
            dbConfig.getString(DB_PASSWORD)
        )

        return entityManagerFactoryFactory.create(
            entitiesSet.name,
            entitiesSet.content.toList(),
            DbEntityManagerConfiguration(dbSource),
        )
    }
}