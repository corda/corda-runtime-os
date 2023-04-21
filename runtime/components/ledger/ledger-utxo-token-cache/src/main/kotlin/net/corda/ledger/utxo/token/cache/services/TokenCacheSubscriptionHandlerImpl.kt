package net.corda.ledger.utxo.token.cache.services

import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.ledger.utxo.token.cache.factories.TokenCacheEventProcessorFactory
import net.corda.libs.configuration.SmartConfig
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
import net.corda.utilities.debug
import org.slf4j.LoggerFactory

class TokenCacheSubscriptionHandlerImpl constructor(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val subscriptionFactory: SubscriptionFactory,
    private val tokenCacheEventProcessorFactory: TokenCacheEventProcessorFactory,
    private val toServiceConfig: (Map<String, SmartConfig>) -> SmartConfig
) : TokenCacheSubscriptionHandler {

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val CONSUMER_GROUP = "TokenEventConsumer"
    }

    private val coordinator =
        coordinatorFactory.createCoordinator<TokenCacheSubscriptionHandler> { event, _ -> eventHandler(event) }
    private var subscription: StateAndEventSubscription<TokenPoolCacheKey, TokenPoolCacheState, TokenPoolCacheEvent>? =
        null
    private var subscriptionRegistrationHandle: RegistrationHandle? = null

    override fun onConfigChange(config: Map<String, SmartConfig>) {
        try {
            val messagingConfig = toServiceConfig(config)

            // close the lifecycle registration first to prevent a down signal to the coordinator
            subscriptionRegistrationHandle?.close()
            subscription?.close()

            subscription = subscriptionFactory.createStateAndEventSubscription(
                SubscriptionConfig(CONSUMER_GROUP, Schemas.Services.TOKEN_CACHE_EVENT),
                tokenCacheEventProcessorFactory.create(),
                messagingConfig
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
