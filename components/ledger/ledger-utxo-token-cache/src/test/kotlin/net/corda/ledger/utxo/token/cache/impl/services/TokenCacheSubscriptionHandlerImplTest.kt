package net.corda.ledger.utxo.token.cache.impl.services

import net.corda.data.flow.event.FlowEvent
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.ledger.utxo.token.cache.factories.TokenCacheEventProcessorFactory
import net.corda.ledger.utxo.token.cache.impl.MINIMUM_SMART_CONFIG
import net.corda.ledger.utxo.token.cache.services.ServiceConfiguration
import net.corda.ledger.utxo.token.cache.services.TokenCacheSubscriptionHandler
import net.corda.ledger.utxo.token.cache.services.TokenSelectionDelegatedProcessor
import net.corda.ledger.utxo.token.cache.services.internal.TokenCacheSubscriptionHandlerImpl
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.test.impl.LifecycleTest
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Services.TOKEN_CACHE_EVENT
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TokenCacheSubscriptionHandlerImplTest {
    private val rpcSubscription = mock<RPCSubscription<TokenPoolCacheEvent, FlowEvent>>()
    private val subscriptionFactory = mock<SubscriptionFactory>().apply {
            whenever(createHttpRPCSubscription<TokenPoolCacheEvent, FlowEvent>(any(), any())).thenReturn(
                rpcSubscription)
    }
    private val serviceConfiguration = mock<ServiceConfiguration>()
    private val toServiceConfig: (Map<String, SmartConfig>) -> SmartConfig = { _ -> MINIMUM_SMART_CONFIG }
    private val toTokenConfig: (Map<String, SmartConfig>) -> SmartConfig = { _ -> MINIMUM_SMART_CONFIG }
    private val toStateManagerConfig: (Map<String, SmartConfig>) -> SmartConfig = { _ -> MINIMUM_SMART_CONFIG }
    private val stateManager = mock<StateManager>()
    private val stateManagerFactory = mock<StateManagerFactory>().apply {
        whenever(create(any())).thenReturn(stateManager)
    }
    private val stateAndEventProcessor =
        mock<StateAndEventProcessor<TokenPoolCacheKey, TokenPoolCacheState, TokenPoolCacheEvent>>()
    private val delegatedStateAndEventProcessor = mock<TokenSelectionDelegatedProcessor>()
    private val tokenCacheEventProcessorFactory = mock<TokenCacheEventProcessorFactory>().apply {
        whenever(create()).thenReturn(stateAndEventProcessor)
        whenever(
            createDelegatedProcessor(
                stateManager,
                stateAndEventProcessor
            )
        ).thenReturn(delegatedStateAndEventProcessor)
    }

    @Test
    fun `start should start the coordinator`() {
        val context = getTokenCacheSubscriptionHandlerTestContext()

        context.run {
            testClass.start()
            verifyIsUp<TokenCacheSubscriptionHandler>()
        }
    }

    @Test
    fun `stop should stop the coordinator`() {
        val context = getTokenCacheSubscriptionHandlerTestContext()

        context.run {
            testClass.start()
            testClass.stop()
            verifyIsDown<TokenCacheSubscriptionHandler>()
        }
    }

    @Test
    fun `on configuration change should create new subscription`() {
        val context = getTokenCacheSubscriptionHandlerTestContext()
        val subName = LifecycleCoordinatorName("sub1")
        val sub =
            mock<StateAndEventSubscription<TokenPoolCacheKey, TokenPoolCacheState, TokenPoolCacheEvent>>().apply {
                whenever(subscriptionName).thenReturn(subName)
            }

        whenever(
            subscriptionFactory.createStateAndEventSubscription(
                SubscriptionConfig("TokenEventConsumer", TOKEN_CACHE_EVENT),
                stateAndEventProcessor,
                MINIMUM_SMART_CONFIG
            )
        ).thenReturn(sub)

        context.run {
            addDependency(subName)
            testClass.start()

            testClass.onConfigChange(mapOf("key" to MINIMUM_SMART_CONFIG))

            verifyIsUp<TokenCacheSubscriptionHandler>()

            verify(sub).start()
        }
    }

    @Test
    fun `on configuration change should close existing subscriptions`() {
        val context = getTokenCacheSubscriptionHandlerTestContext()
        val subName = LifecycleCoordinatorName("sub1")
        val sub1 =
            mock<StateAndEventSubscription<TokenPoolCacheKey, TokenPoolCacheState, TokenPoolCacheEvent>>().apply {
                whenever(subscriptionName).thenReturn(subName)
            }

        val sub2 =
            mock<StateAndEventSubscription<TokenPoolCacheKey, TokenPoolCacheState, TokenPoolCacheEvent>>().apply {
                whenever(subscriptionName).thenReturn(subName)
            }

        whenever(
            subscriptionFactory.createStateAndEventSubscription(
                SubscriptionConfig("TokenEventConsumer", TOKEN_CACHE_EVENT),
                stateAndEventProcessor,
                MINIMUM_SMART_CONFIG
            )
        ).thenReturn(sub1, sub2)

        context.run {
            addDependency(subName)
            testClass.start()

            testClass.onConfigChange(mapOf("key" to MINIMUM_SMART_CONFIG))
            testClass.onConfigChange(mapOf("key" to MINIMUM_SMART_CONFIG))

            verifyIsUp<TokenCacheSubscriptionHandler>()
            verify(sub1).start()
            verify(sub2).start()
            verify(sub1).close()
        }
    }

    @Test
    fun `configuration exception should set the component to error state`() {
        val context = getTokenCacheSubscriptionHandlerTestContext()
        val subName = LifecycleCoordinatorName("sub1")

        whenever(tokenCacheEventProcessorFactory.create()).thenThrow(IllegalStateException())

        context.run {
            addDependency(subName)
            testClass.start()

            testClass.onConfigChange(mapOf("key" to MINIMUM_SMART_CONFIG))

            verifyIsInError<TokenCacheSubscriptionHandler>()
        }
    }

    private fun getTokenCacheSubscriptionHandlerTestContext(): LifecycleTest<TokenCacheSubscriptionHandlerImpl> {
        return LifecycleTest {
            addDependency<LifecycleCoordinatorFactory>()

            TokenCacheSubscriptionHandlerImpl(
                coordinatorFactory,
                subscriptionFactory,
                tokenCacheEventProcessorFactory,
                serviceConfiguration,
                stateManagerFactory,
                toServiceConfig,
                toTokenConfig,
                toStateManagerConfig
            )
        }
    }
}
