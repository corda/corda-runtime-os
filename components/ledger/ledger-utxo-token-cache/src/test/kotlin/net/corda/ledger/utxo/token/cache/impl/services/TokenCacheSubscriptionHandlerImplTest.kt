package net.corda.ledger.utxo.token.cache.impl.services

import net.corda.data.flow.event.FlowEvent
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.ledger.utxo.token.cache.factories.TokenCacheEventProcessorFactory
import net.corda.ledger.utxo.token.cache.impl.MINIMUM_SMART_CONFIG
import net.corda.ledger.utxo.token.cache.services.ServiceConfiguration
import net.corda.ledger.utxo.token.cache.services.TokenCacheSubscriptionHandler
import net.corda.ledger.utxo.token.cache.services.TokenSelectionSyncRPCProcessor
import net.corda.ledger.utxo.token.cache.services.internal.TokenCacheSubscriptionHandlerImpl
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.test.impl.LifecycleTest
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.StateManagerConfig
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TokenCacheSubscriptionHandlerImplTest {
    private val rpcSubscription = mock<RPCSubscription<TokenPoolCacheEvent, FlowEvent>>()
    private val subscriptionFactory = mock<SubscriptionFactory>().apply {
        whenever(createHttpRPCSubscription<TokenPoolCacheEvent, FlowEvent>(any(), any())).thenReturn(
            rpcSubscription
        )
    }
    private val serviceConfiguration = mock<ServiceConfiguration>()
    private val toTokenConfig: (Map<String, SmartConfig>) -> SmartConfig = { _ -> MINIMUM_SMART_CONFIG }
    private val toStateManagerConfig: (Map<String, SmartConfig>) -> SmartConfig = { _ -> MINIMUM_SMART_CONFIG }
    private val stateManager = mock<StateManager>()
    private val stateManagerFactory = mock<StateManagerFactory>().apply {
        whenever(create(any(), eq(StateManagerConfig.StateType.TOKEN_POOL_CACHE), anyOrNull())).thenReturn(stateManager)
    }
    private val tokenSelectionSyncRPCProcessor = mock<TokenSelectionSyncRPCProcessor>()
    private val tokenCacheEventProcessorFactory = mock<TokenCacheEventProcessorFactory>().apply {
        whenever(
            createTokenSelectionSyncRPCProcessor(
                any()
            )
        ).thenReturn(tokenSelectionSyncRPCProcessor)
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

        val mediatorName = LifecycleCoordinatorName("mediator")

        val stateManagerName = LifecycleCoordinatorName("stateManager")
        val stateManager = mock<StateManager>().apply { whenever(name).thenReturn(stateManagerName) }

        whenever(stateManagerFactory.create(any(), eq(StateManagerConfig.StateType.TOKEN_POOL_CACHE), anyOrNull()))
            .thenReturn(stateManager)

        context.run {
            addDependency(mediatorName)
            addDependency(stateManagerName)

            testClass.start()
            testClass.onConfigChange(mapOf("key" to MINIMUM_SMART_CONFIG))

            verifyIsUp<TokenCacheSubscriptionHandler>()

            verify(stateManager).start()
        }
    }

    @Test
    fun `on configuration change should close existing subscriptions`() {
        val context = getTokenCacheSubscriptionHandlerTestContext()
        val subName = LifecycleCoordinatorName("sub1")
        val stateManagerName = LifecycleCoordinatorName("stateManager")

        val stateManager1 = mock<StateManager>().apply { whenever(name).thenReturn(stateManagerName) }
        val stateManager2 = mock<StateManager>().apply { whenever(name).thenReturn(stateManagerName) }

        whenever(stateManagerFactory.create(any(), eq(StateManagerConfig.StateType.TOKEN_POOL_CACHE), anyOrNull()))
            .thenReturn(stateManager1, stateManager2)

        context.run {
            addDependency(subName)
            addDependency(stateManagerName)
            testClass.start()

            testClass.onConfigChange(mapOf("key" to MINIMUM_SMART_CONFIG))
            testClass.onConfigChange(mapOf("key" to MINIMUM_SMART_CONFIG))

            verifyIsUp<TokenCacheSubscriptionHandler>()
            verify(stateManager1).start()
            verify(stateManager2).start()
            verify(stateManager1).stop()
        }
    }

    @Test
    fun `configuration exception should set the component to error state`() {
        val context = getTokenCacheSubscriptionHandlerTestContext()
        val subName = LifecycleCoordinatorName("sub1")

        whenever(tokenCacheEventProcessorFactory.createTokenSelectionSyncRPCProcessor(any())).thenThrow(IllegalStateException())

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
                toTokenConfig,
                toStateManagerConfig
            )
        }
    }
}
