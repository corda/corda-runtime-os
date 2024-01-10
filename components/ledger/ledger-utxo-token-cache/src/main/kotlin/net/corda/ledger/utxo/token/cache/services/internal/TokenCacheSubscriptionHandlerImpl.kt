package net.corda.ledger.utxo.token.cache.services.internal

import net.corda.ledger.utxo.token.cache.factories.TokenCacheEventProcessorFactory
import net.corda.ledger.utxo.token.cache.services.ServiceConfiguration
import net.corda.ledger.utxo.token.cache.services.TokenCacheSubscriptionHandler
import net.corda.ledger.utxo.token.cache.services.TokenSelectionSyncHttpProcessor
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
import net.corda.messaging.api.constants.WorkerHttpPaths
import net.corda.messaging.api.subscription.config.SyncHttpConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.utilities.debug
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
class TokenCacheSubscriptionHandlerImpl(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val subscriptionFactory: SubscriptionFactory,
    private val tokenCacheEventProcessorFactory: TokenCacheEventProcessorFactory,
    private val serviceConfiguration: ServiceConfiguration,
    private val stateManagerFactory: StateManagerFactory,
    private val toTokenConfig: (Map<String, SmartConfig>) -> SmartConfig,
    private val toStateManagerConfig: (Map<String, SmartConfig>) -> SmartConfig,
) : TokenCacheSubscriptionHandler {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val RPC_SUBSCRIPTION = "TOKEN_RPC_SUBSCRIPTION"
        private val config = SyncHttpConfig("Token Selection Processor", WorkerHttpPaths.TOKEN_SELECTION_PATH)
    }

    private val coordinator =
        coordinatorFactory.createCoordinator<TokenCacheSubscriptionHandler> { event, _ -> eventHandler(event) }
    private var stateManager: StateManager? = null
    private var subscriptionRegistrationHandle: RegistrationHandle? = null

    override fun onConfigChange(config: Map<String, SmartConfig>) {
        try {
            serviceConfiguration.init(toTokenConfig(config))

            // close the lifecycle registration first to prevent a down signal to the coordinator
            subscriptionRegistrationHandle?.close()
            stateManager?.stop()

            // Create a new state manager and the token selection rpc processor
            val messagingConfig = toStateManagerConfig(config)
            val localStateManager = stateManagerFactory.create(messagingConfig)
            val processor = tokenCacheEventProcessorFactory.createTokenSelectionSyncHttpProcessor(localStateManager)

            // Create the HTTP RPC subscription
            createAndRegisterSyncHttpSubscription(processor)

            subscriptionRegistrationHandle = coordinator.followStatusChangesByName(
                setOf(localStateManager.name)
            )

            localStateManager.start()
            stateManager = localStateManager
        } catch (ex: Exception) {
            val reason = "Failed to configure the Token Event Handler using '$config'"
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
                stateManager?.stop()
                stateManager = null
                log.debug { "Token Cache configuration handler stopped" }
            }
        }
    }

    private fun createAndRegisterSyncHttpSubscription(processor: TokenSelectionSyncHttpProcessor) {
        coordinator.createManagedResource(RPC_SUBSCRIPTION) {
            subscriptionFactory.createSyncHttpSubscription(
                config,
                processor
            ).also {
                it.start()
            }
        }
    }
}
