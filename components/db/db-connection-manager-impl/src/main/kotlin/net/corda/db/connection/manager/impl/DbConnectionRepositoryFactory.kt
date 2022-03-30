package net.corda.db.connection.manager.impl

import net.corda.db.connection.manager.DbConnectionsRepository
import net.corda.db.connection.manager.createFromConfig
import net.corda.db.core.CloseableDataSource
import net.corda.db.core.DataSourceFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.datamodel.ConfigurationEntities
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import javax.persistence.EntityManagerFactory

/**
 * Factory for creating [DbConnectionsRepository] instances
 */
class DbConnectionRepositoryFactory {

    fun create(
        clusterDataSource: CloseableDataSource,
        config: SmartConfig,
        dataSourceFactory: DataSourceFactory,
        entityManagerFactoryFactory: EntityManagerFactoryFactory
    ): DbConnectionsRepository {
        return DbConnectionsRepositoryImpl(
            clusterDataSource,
            dataSourceFactory,
            entityManagerFactoryFactory.create(
                "DB Connections",
                ConfigurationEntities.classes.toList(),
                DbEntityManagerConfiguration(dataSourceFactory.createFromConfig(config))
            ),
            config.factory)
    }

    fun create(
        clusterDataSource: CloseableDataSource,
        dataSourceFactory: DataSourceFactory,
        entityManagerFactory: EntityManagerFactory,
        configFactory: SmartConfigFactory
    ): DbConnectionsRepository {
        return DbConnectionsRepositoryImpl(clusterDataSource, dataSourceFactory, entityManagerFactory, configFactory)
    }
}