package net.corda.libs.statemanager.impl.factory

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.db.core.PostgresDataSourceFactory
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
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
    @Reference(service = CordaAvroSerializationFactory::class)
    private val avroSerializationFactory: CordaAvroSerializationFactory,
) : StateManagerFactory {

    override fun create(config: SmartConfig): StateManager {
        val dataSource = PostgresDataSourceFactory().create(
            jdbcUrl = config.getString("database.jdbc.url"),
            username = config.getString("database.user"),
            password = config.getString("database.pass"),
        )
        val entityManagerFactory = entityManagerFactoryFactory.create(
            CordaDb.StateManager.persistenceUnitName,
            StateManagerEntities.classes,
            DbEntityManagerConfiguration(dataSource)
        )
        return StateManagerImpl(
            StateManagerRepositoryImpl(),
            entityManagerFactory,
            avroSerializationFactory
        )
    }
}