package net.corda.libs.permissions.storage.common.db

import net.corda.db.core.PostgresDataSourceFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.storage.common.ConfigKeys.DB_PASSWORD
import net.corda.libs.permissions.storage.common.ConfigKeys.DB_URL
import net.corda.libs.permissions.storage.common.ConfigKeys.DB_USER
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntitiesSet
import net.corda.orm.EntityManagerFactoryFactory
import javax.persistence.EntityManagerFactory

object DbUtils {

    fun obtainEntityManagerFactory(
        dbConfig: SmartConfig, entityManagerFactoryFactory: EntityManagerFactoryFactory,
        entitiesSet: EntitiesSet
    ): EntityManagerFactory {

        val dbSource = PostgresDataSourceFactory().create(
            dbConfig.getString(DB_URL),
            dbConfig.getString(DB_USER),
            dbConfig.getString(DB_PASSWORD)
        )

        return entityManagerFactoryFactory.create(
            entitiesSet.name,
            entitiesSet.content.toList(),
            DbEntityManagerConfiguration(dbSource),
        )
    }
}