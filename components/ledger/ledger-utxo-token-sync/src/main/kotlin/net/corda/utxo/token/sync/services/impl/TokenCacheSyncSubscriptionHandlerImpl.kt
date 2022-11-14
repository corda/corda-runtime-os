package net.corda.utxo.token.sync.services.impl

import net.corda.data.ledger.utxo.token.selection.event.TokenSyncEvent
import net.corda.data.ledger.utxo.token.selection.state.TokenSyncState
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
import net.corda.schema.Schemas.Services.Companion.TOKEN_CACHE_SYNC_EVENT
import net.corda.utxo.token.sync.factories.TokenCacheSyncRequestProcessorFactory
import net.corda.utxo.token.sync.services.TokenCacheSyncSubscriptionHandler
import net.corda.utxo.token.sync.services.SyncWakeUpScheduler
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug

class TokenCacheSyncSubscriptionHandlerImpl constructor(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val subscriptionFactory: SubscriptionFactory,
    private val tokenCacheSyncRequestProcessorFactory: TokenCacheSyncRequestProcessorFactory,
    private val syncWakeUpScheduler: SyncWakeUpScheduler,
    private val toServiceConfig: (Map<String, SmartConfig>) -> SmartConfig
) : TokenCacheSyncSubscriptionHandler {

    companion object {
        private val log = contextLogger()
        private const val CONSUMER_GROUP = "TokenSyncEventConsumer"
    }

    private val coordinator =
        coordinatorFactory.createCoordinator<TokenCacheSyncSubscriptionHandler> { event, _ -> eventHandler(event) }
    private var subscription: StateAndEventSubscription<String, TokenSyncState, TokenSyncEvent>? =
        null
    private var subscriptionRegistrationHandle: RegistrationHandle? = null

    override fun onConfigChange(config: Map<String, SmartConfig>) {
        try {
            val messagingConfig = toServiceConfig(config)

            // close the lifecycle registration first to prevent a down signal to the coordinator
            subscriptionRegistrationHandle?.close()
            subscription?.close()

            subscription = subscriptionFactory.createStateAndEventSubscription(
                SubscriptionConfig(CONSUMER_GROUP, TOKEN_CACHE_SYNC_EVENT),
                tokenCacheSyncRequestProcessorFactory.create(),
                messagingConfig,
                syncWakeUpScheduler
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
                log.debug { "Token Sync subscription handler is stopping..." }
                subscriptionRegistrationHandle?.close()
                subscription?.close()
                log.debug { "Token Sync subscription handler stopped" }
            }
        }
    }
}
