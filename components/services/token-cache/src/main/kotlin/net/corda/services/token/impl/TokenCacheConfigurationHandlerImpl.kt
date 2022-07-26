package net.corda.services.token.impl

import net.corda.data.services.TokenEvent
import net.corda.data.services.TokenSetKey
import net.corda.data.services.TokenState
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.services.token.ClaimTimeoutScheduler
import net.corda.services.token.TokenCacheConfigurationHandler
import net.corda.services.token.TokenCacheEventHandlerFactory
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

    @Suppress("LongParameterList")
    @Component(service = [TokenCacheConfigurationHandlerImpl::class])
    class TokenCacheConfigurationHandlerImpl constructor(
        coordinatorFactory: LifecycleCoordinatorFactory,
        private val subscriptionFactory: SubscriptionFactory,
        private val tokenCacheEventHandlerFactory: TokenCacheEventHandlerFactory,
        private val claimTimeoutScheduler: ClaimTimeoutScheduler,
        private val toMessagingConfig: (Map<String, SmartConfig>) -> SmartConfig
    ) : TokenCacheConfigurationHandler {

        @Activate
        constructor(
            @Reference(service = LifecycleCoordinatorFactory::class)
            coordinatorFactory: LifecycleCoordinatorFactory,
            @Reference(service = SubscriptionFactory::class)
            subscriptionFactory: SubscriptionFactory,
            @Reference(service = TokenCacheEventHandlerFactory::class)
            tokenCacheEventHandlerFactory: TokenCacheEventHandlerFactory,
            @Reference(service = ClaimTimeoutScheduler::class)
            claimTimeoutScheduler: ClaimTimeoutScheduler
        ) : this(
            coordinatorFactory,
            subscriptionFactory,
            tokenCacheEventHandlerFactory,
            claimTimeoutScheduler,
            { cfg -> cfg.getConfig(ConfigKeys.MESSAGING_CONFIG) }
        )

        companion object {
            private val log = contextLogger()
            private const val CONSUMER_GROUP = "TokenEventConsumer"
        }

        private val coordinator = coordinatorFactory.createCoordinator<TokenCacheConfigurationHandler> { event, _ -> eventHandler(event) }
        private var subscription: StateAndEventSubscription<TokenSetKey, TokenState, TokenEvent>? = null
        private var subscriptionRegistrationHandle: RegistrationHandle? = null

        override fun onConfigChange(config: Map<String, SmartConfig>) {
            try {
                val messagingConfig = toMessagingConfig(config)
                val flowConfig = checkNotNull(config[ConfigKeys.SERVICES_CONFIG]) { "Failed to find a SERVICES_CONFIG section in '${config}'" }

                // close the lifecycle registration first to prevent down being signaled
                subscriptionRegistrationHandle?.close()
                subscription?.close()

                subscription = subscriptionFactory.createStateAndEventSubscription(
                    SubscriptionConfig(CONSUMER_GROUP, Schemas.Services.TOKEN_SELECTION_EVENT),
                    tokenCacheEventHandlerFactory.create(flowConfig),
                    messagingConfig,
                    claimTimeoutScheduler
                )

                subscriptionRegistrationHandle = coordinator.followStatusChangesByName(
                    setOf(subscription!!.subscriptionName)
                )

                subscription?.start()
            } catch (ex: Exception) {
                val reason = "Failed to configure the Token Event Handler using '${config}'"
                log.error(reason, ex)
                coordinator.updateStatus(LifecycleStatus.ERROR, reason)
            }
        }

        override val isRunning: Boolean
            get() = coordinator.isRunning

        override fun start() {
            coordinator.start()
        }

        override fun stop() {
            coordinator.stop()
        }

        private fun eventHandler(event: LifecycleEvent) {
            when (event) {
                is StartEvent -> {
                    coordinator.updateStatus(LifecycleStatus.UP)
                }
                is StopEvent -> {
                    log.debug { "Token Cache configuration handler is stopping..." }
                    subscriptionRegistrationHandle?.close()
                    subscription?.close()
                    log.debug { "Token Cache configuration handler stopped" }
                }
            }
        }
    }
