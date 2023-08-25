package net.corda.ledger.utxo.token.cache.factories

import net.corda.configuration.read.ConfigurationReadService
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.utxo.token.cache.converters.EntityConverterImpl
import net.corda.ledger.utxo.token.cache.converters.EventConverterImpl
import net.corda.ledger.utxo.token.cache.entities.TokenEvent
import net.corda.ledger.utxo.token.cache.entities.internal.TokenPoolCacheImpl
import net.corda.ledger.utxo.token.cache.handlers.TokenBalanceQueryEventHandler
import net.corda.ledger.utxo.token.cache.handlers.TokenClaimQueryEventHandler
import net.corda.ledger.utxo.token.cache.handlers.TokenClaimReleaseEventHandler
import net.corda.ledger.utxo.token.cache.handlers.TokenEventHandler
import net.corda.ledger.utxo.token.cache.handlers.TokenLedgerChangeEventHandler
import net.corda.ledger.utxo.token.cache.queries.impl.SqlQueryProviderTokens
import net.corda.ledger.utxo.token.cache.repositories.impl.UtxoTokenRepositoryImpl
import net.corda.ledger.utxo.token.cache.services.internal.ServiceConfigurationImpl
import net.corda.ledger.utxo.token.cache.services.SimpleTokenFilterStrategy
import net.corda.ledger.utxo.token.cache.services.TokenCacheComponent
import net.corda.ledger.utxo.token.cache.services.internal.TokenCacheSubscriptionHandlerImpl
import net.corda.ledger.utxo.token.cache.services.internal.AvailableTokenServiceImpl
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.schema.configuration.ConfigKeys
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
    @Reference(service = ExternalEventResponseFactory::class)
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoService: VirtualNodeInfoReadService,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = JpaEntitiesRegistry::class)
    private val jpaEntitiesRegistry: JpaEntitiesRegistry
) {
    fun create(): TokenCacheComponent {

        val entityConverter = EntityConverterImpl()
        val eventConverter = EventConverterImpl(entityConverter)
        val recordFactory = RecordFactoryImpl(externalEventResponseFactory)
        val tokenFilterStrategy = SimpleTokenFilterStrategy()
        val sqlQueryProvider = SqlQueryProviderTokens()
        val utxoTokenRepository = UtxoTokenRepositoryImpl(sqlQueryProvider)
        val tokenPoolCache = TokenPoolCacheImpl()
        val serviceConfiguration = ServiceConfigurationImpl()
        val availableTokenService = AvailableTokenServiceImpl(
            virtualNodeInfoService,
            dbConnectionManager,
            jpaEntitiesRegistry,
            utxoTokenRepository,
            serviceConfiguration
        )


        val eventHandlerMap = mapOf<Class<*>, TokenEventHandler<in TokenEvent>>(
            createHandler(
                TokenClaimQueryEventHandler(
                    tokenFilterStrategy,
                    recordFactory,
                    availableTokenService
                )
            ),
            createHandler(TokenClaimReleaseEventHandler(recordFactory)),
            createHandler(TokenLedgerChangeEventHandler()),
            createHandler(TokenBalanceQueryEventHandler(recordFactory, availableTokenService)),
        )

        val tokenCacheEventHandlerFactory = TokenCacheEventProcessorFactoryImpl(
            eventConverter,
            entityConverter,
            tokenPoolCache,
            eventHandlerMap
        )

        val tokenCacheConfigurationHandler = TokenCacheSubscriptionHandlerImpl(
            coordinatorFactory,
            subscriptionFactory,
            tokenCacheEventHandlerFactory,
            serviceConfiguration,
            { cfg -> cfg.getConfig(ConfigKeys.MESSAGING_CONFIG) },
            { cfg -> cfg.getConfig(ConfigKeys.UTXO_LEDGER_CONFIG) }
        )

        return TokenCacheComponent(
            coordinatorFactory,
            configurationReadService,
            tokenCacheConfigurationHandler,
        )
    }

    private inline fun <reified T : TokenEvent> createHandler(
        handler: TokenEventHandler<in T>
    ): Pair<Class<T>, TokenEventHandler<in TokenEvent>> {
        @Suppress("unchecked_cast")
        return Pair(T::class.java, handler as TokenEventHandler<in TokenEvent>)
    }
}
