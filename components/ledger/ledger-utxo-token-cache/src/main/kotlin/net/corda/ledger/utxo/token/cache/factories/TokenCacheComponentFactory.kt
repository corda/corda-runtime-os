package net.corda.ledger.utxo.token.cache.factories

import net.corda.configuration.read.ConfigurationReadService
import net.corda.ledger.utxo.token.cache.services.ServiceConfiguration
import net.corda.ledger.utxo.token.cache.services.TokenCacheComponent
import net.corda.ledger.utxo.token.cache.services.internal.TokenCacheSubscriptionHandlerImpl
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys
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
    @Reference(service = TokenCacheEventProcessorFactory::class)
    private val tokenCacheEventHandlerFactory: TokenCacheEventProcessorFactory,
    @Reference(service = ServiceConfiguration::class)
    private val serviceConfiguration: ServiceConfiguration
) {
    fun create(): TokenCacheComponent {
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
}
