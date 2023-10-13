package net.corda.ledger.utxo.token.cache.services.internal

import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.ledger.utxo.token.cache.factories.TokenCacheEventProcessorFactory
import net.corda.ledger.utxo.token.cache.services.ServiceConfiguration
import net.corda.ledger.utxo.token.cache.services.TokenCacheSubscriptionHandler
import net.corda.ledger.utxo.token.cache.services.TokenSelectionDelegatedProcessor
import net.corda.ledger.utxo.token.cache.services.TokenSelectionSyncRPCProcessor
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.constants.WorkerRPCPaths
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.config.SyncRPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.utilities.debug
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
class TokenCacheSubscriptionHandlerImpl(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val subscriptionFactory: SubscriptionFactory,
    private val tokenCacheEventProcessorFactory: TokenCacheEventProcessorFactory,
    private val serviceConfiguration: ServiceConfiguration,
    private val stateManagerFactory: StateManagerFactory,
    private val toMessagingConfig: (Map<String, SmartConfig>) -> SmartConfig,
    private val toTokenConfig: (Map<String, SmartConfig>) -> SmartConfig,
    private val toStateManagerConfig: (Map<String, SmartConfig>) -> SmartConfig,
) : TokenCacheSubscriptionHandler {

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val CONSUMER_GROUP = "TokenEventConsumer"
        private const val SUBSCRIPTION_NAME = "Token Selection Processor"
        private const val RPC_SUBSCRIPTION = "TOKEN_RPC_SUBSCRIPTION"
    }

    private val coordinator =
        coordinatorFactory.createCoordinator<TokenCacheSubscriptionHandler> { event, _ -> eventHandler(event) }
    private var subscription: StateAndEventSubscription<TokenPoolCacheKey, TokenPoolCacheState, TokenPoolCacheEvent>? =
        null
    private var subscriptionRegistrationHandle: RegistrationHandle? = null
    private var stateManager: StateManager? = null

    override fun onConfigChange(config: Map<String, SmartConfig>) {
        try {
            serviceConfiguration.init(toTokenConfig(config))
            val messagingConfig = toMessagingConfig(config)
            val newStateManagerConfig = toStateManagerConfig(config)

            stateManager?.close()
            stateManager = stateManagerFactory.create(newStateManagerConfig)

            // close the lifecycle registration first to prevent a down signal to the coordinator
            subscriptionRegistrationHandle?.close()
            subscription?.close()

            // Create the State and Event subscription
            val processor = tokenCacheEventProcessorFactory.create()

            subscription = subscriptionFactory.createStateAndEventSubscription(
                SubscriptionConfig(CONSUMER_GROUP, Schemas.Services.TOKEN_CACHE_EVENT),
                processor,
                messagingConfig
            )

            val delegatedProcessor = tokenCacheEventProcessorFactory.createDelegatedProcessor(stateManager!!, processor)

            // Create the HTTP RPC subscription
            createAndRegisterSyncRPCSubscription(delegatedProcessor)

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

    private fun createAndRegisterSyncRPCSubscription(delegatedProcessor: TokenSelectionDelegatedProcessor) {
        val processor = TokenSelectionSyncRPCProcessor(delegatedProcessor)
        val rpcConfig = SyncRPCConfig(SUBSCRIPTION_NAME, WorkerRPCPaths.TOKEN_SELECTION_PATH)
        coordinator.createManagedResource(RPC_SUBSCRIPTION) {
            subscriptionFactory.createHttpRPCSubscription(
                rpcConfig,
                processor
            ).also {
                it.start()
            }
        }
    }
}
