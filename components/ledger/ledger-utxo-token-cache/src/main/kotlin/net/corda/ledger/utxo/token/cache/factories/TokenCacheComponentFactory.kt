package net.corda.ledger.utxo.token.cache.factories

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.utxo.token.cache.converters.EntityConverterImpl
import net.corda.ledger.utxo.token.cache.converters.EventConverterImpl
import net.corda.ledger.utxo.token.cache.services.ServiceConfiguration
import net.corda.ledger.utxo.token.cache.services.TokenCacheComponent
import net.corda.ledger.utxo.token.cache.services.TokenPoolCacheStateSerializationImpl
import net.corda.ledger.utxo.token.cache.services.internal.TokenCacheSubscriptionHandlerImpl
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.time.UTCClock
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("LongParameterList", "Unused")
@Component(service = [TokenCacheComponentFactory::class])
class TokenCacheComponentFactory @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = ServiceConfiguration::class)
    private val serviceConfiguration: ServiceConfiguration,
    @Reference(service = StateManagerFactory::class)
    private val stateManagerFactory: StateManagerFactory,
    @Reference(service = ExternalEventResponseFactory::class)
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoService: VirtualNodeInfoReadService,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = JpaEntitiesRegistry::class)
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory
) {
    fun create(): TokenCacheComponent {
        val clock = UTCClock()
        val entityConverter = EntityConverterImpl(serviceConfiguration, clock)
        val eventConverter = EventConverterImpl(entityConverter)
        val tokenSerialization = TokenPoolCacheStateSerializationImpl(cordaAvroSerializationFactory)
        val tokenCacheEventProcessorFactory = TokenCacheEventProcessorFactoryImpl(
            serviceConfiguration,
            externalEventResponseFactory,
            virtualNodeInfoService,
            dbConnectionManager,
            jpaEntitiesRegistry,
            entityConverter,
            eventConverter,
            tokenSerialization,
            clock
        )

        val tokenCacheConfigurationHandler = TokenCacheSubscriptionHandlerImpl(
            coordinatorFactory,
            subscriptionFactory,
            tokenCacheEventProcessorFactory,
            serviceConfiguration,
            stateManagerFactory,
            { cfg -> cfg.getConfig(ConfigKeys.UTXO_LEDGER_CONFIG) },
            { cfg -> cfg.getConfig(ConfigKeys.STATE_MANAGER_CONFIG) }
        )

        return TokenCacheComponent(
            coordinatorFactory,
            configurationReadService,
            tokenCacheConfigurationHandler,
        )
    }
}
