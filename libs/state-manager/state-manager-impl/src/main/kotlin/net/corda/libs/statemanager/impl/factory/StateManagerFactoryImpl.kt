package net.corda.libs.statemanager.impl.factory

import java.time.Duration
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.db.core.DataSourceFactory
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.StateManager
import net.corda.libs.statemanager.StateManagerFactory
import net.corda.libs.statemanager.impl.StateManagerImpl
import net.corda.libs.statemanager.impl.model.v1_0.StateManagerEntities
import net.corda.libs.statemanager.impl.repository.impl.StateManagerRepositoryImpl
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [StateManagerFactory::class])
class StateManagerFactoryImpl @Activate constructor(
    @Reference(service = EntityManagerFactoryFactory::class)
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory,
    @Reference(service = DataSourceFactory::class)
    private val dataSourceFactory: DataSourceFactory,
    @Reference(service = CordaAvroSerializationFactory::class)
    private val avroSerializationFactory: CordaAvroSerializationFactory,
) : StateManagerFactory {

    override fun create(config: SmartConfig): StateManager {
        val dataSource = dataSourceFactory.create(
            driverClass = "org.postgresql.Driver",
            jdbcUrl = config.getString("database.jdbc.url"),
            username = config.getString("database.user"),
            password = config.getString("database.pass"),
            maximumPoolSize = 1,
            minimumPoolSize = null,
            idleTimeout = Duration.ofMinutes(2),
            maxLifetime = Duration.ofMinutes(30),
            keepaliveTime = Duration.ZERO,
            validationTimeout = Duration.ofSeconds(5),
        )
        val entityManagerFactory = entityManagerFactoryFactory.create(
            CordaDb.StateManager.persistenceUnitName,
            StateManagerEntities.classes,
            DbEntityManagerConfiguration(
                dataSource
            )
        )
        return StateManagerImpl(
            StateManagerRepositoryImpl(),
            entityManagerFactory,
            avroSerializationFactory
        )
    }
}