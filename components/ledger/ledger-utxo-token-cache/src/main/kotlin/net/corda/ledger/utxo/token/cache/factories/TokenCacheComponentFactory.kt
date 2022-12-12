package net.corda.ledger.utxo.token.cache.factories

import net.corda.configuration.read.ConfigurationReadService
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.utxo.token.cache.converters.EntityConverterImpl
import net.corda.ledger.utxo.token.cache.converters.EventConverterImpl
import net.corda.ledger.utxo.token.cache.entities.TokenEvent
import net.corda.ledger.utxo.token.cache.handlers.TokenClaimQueryEventHandler
import net.corda.ledger.utxo.token.cache.handlers.TokenClaimReleaseEventHandler
import net.corda.ledger.utxo.token.cache.handlers.TokenEventHandler
import net.corda.ledger.utxo.token.cache.handlers.TokenLedgerChangeEventHandler
import net.corda.ledger.utxo.token.cache.services.SimpleTokenFilterStrategy
import net.corda.ledger.utxo.token.cache.services.TokenCacheComponent
import net.corda.ledger.utxo.token.cache.services.TokenCacheSubscriptionHandlerImpl
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.util.uncheckedCast
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
    private val externalEventResponseFactory: ExternalEventResponseFactory
) {
    fun create(): TokenCacheComponent {

        val entityConverter = EntityConverterImpl()
        val eventConverter = EventConverterImpl(entityConverter)
        val recordFactory = RecordFactoryImpl(externalEventResponseFactory)
        val tokenFilterStrategy = SimpleTokenFilterStrategy()

        val eventHandlerMap = mapOf<Class<*>, TokenEventHandler<in TokenEvent>>(
            createHandler(TokenClaimQueryEventHandler(tokenFilterStrategy, recordFactory)),
            createHandler(TokenClaimReleaseEventHandler(recordFactory)),
            createHandler(TokenLedgerChangeEventHandler(externalEventResponseFactory)),
        )

        val tokenCacheEventHandlerFactory = TokenCacheEventProcessorFactoryImpl(
            eventConverter,
            entityConverter,
            eventHandlerMap
        )

        val tokenCacheConfigurationHandler = TokenCacheSubscriptionHandlerImpl(
            coordinatorFactory,
            subscriptionFactory,
            tokenCacheEventHandlerFactory
        ) { cfg -> cfg.getConfig(ConfigKeys.MESSAGING_CONFIG) }

        return TokenCacheComponent(
            coordinatorFactory,
            configurationReadService,
            tokenCacheConfigurationHandler,
        )
    }

    private inline fun <reified T : TokenEvent> createHandler(
        handler: TokenEventHandler<in T>
    ): Pair<Class<T>, TokenEventHandler<in TokenEvent>> {
        return Pair(T::class.java, uncheckedCast(handler))
    }
}
