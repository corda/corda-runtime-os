package net.corda.services.token

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys
import net.corda.services.token.handlers.TokenClaimQueryEventHandler
import net.corda.services.token.handlers.TokenClaimReleaseEventHandler
import net.corda.services.token.handlers.TokenLedgerEventHandler
import net.corda.services.token.handlers.TokenTimeoutCheckEventHandler
import net.corda.services.token.impl.ClaimTimeoutSchedulerImpl
import net.corda.services.token.impl.TokenCacheConfigurationImpl
import net.corda.services.token.impl.TokenCacheEventProcessorFactoryImpl
import net.corda.services.token.impl.TokenCacheSubscriptionHandlerImpl
import net.corda.services.token.impl.TokenRecordFactoryImpl
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.util.uncheckedCast
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.Executors

@Suppress("LongParameterList", "Unused")
@Component(service = [TokenCacheComponentFactory::class])
class TokenCacheComponentFactory constructor(
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    private val configurationReadService: ConfigurationReadService,
    private val subscriptionFactory: SubscriptionFactory,
    private val publisherFactory: PublisherFactory,
    private val clock: Clock
) {

    @Activate
    constructor(
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
        @Reference(service = ConfigurationReadService::class)
        configurationReadService: ConfigurationReadService,
        @Reference(service = SubscriptionFactory::class)
        subscriptionFactory: SubscriptionFactory,
        @Reference(service = PublisherFactory::class)
        publisherFactory: PublisherFactory
    ) : this(coordinatorFactory, configurationReadService, subscriptionFactory, publisherFactory, UTCClock())

    lateinit var tokenCacheEventProcessorFactory: TokenCacheEventProcessorFactory

    fun create(): TokenCacheComponent {
        val clock = UTCClock()
        val tokenRecordFactory = TokenRecordFactoryImpl()
        val tokenCacheConfiguration = TokenCacheConfigurationImpl()

        val eventHandlerMap = mapOf<Class<*>, TokenEventHandler<in Any>>(
            createHandler(TokenClaimQueryEventHandler(clock, tokenRecordFactory, tokenCacheConfiguration)),
            createHandler(TokenLedgerEventHandler()),
            createHandler(TokenTimeoutCheckEventHandler(clock)),
            createHandler(TokenClaimReleaseEventHandler())
            )

        val tokenCacheEventHandlerFactory = TokenCacheEventProcessorFactoryImpl(eventHandlerMap)
        tokenCacheEventProcessorFactory = tokenCacheEventHandlerFactory

        val claimTimeoutScheduler = ClaimTimeoutSchedulerImpl(
            publisherFactory,
            tokenRecordFactory,
            Executors.newSingleThreadScheduledExecutor(),
            UTCClock()
        )

        val tokenCacheConfigurationHandler = TokenCacheSubscriptionHandlerImpl(
            coordinatorFactory,
            subscriptionFactory,
            tokenCacheEventHandlerFactory,
            claimTimeoutScheduler
        ) { cfg -> cfg.getConfig(ConfigKeys.MESSAGING_CONFIG) }

        return TokenCacheComponent(
            coordinatorFactory,
            configurationReadService,
            tokenCacheConfiguration,
            tokenCacheConfigurationHandler,
            claimTimeoutScheduler
        )
    }

    private inline fun <reified T : Any> createHandler(
        handler: TokenEventHandler<in T>
    ): Pair<Class<T>, TokenEventHandler<in Any>> {
        return Pair(T::class.java, uncheckedCast(handler))
    }
}