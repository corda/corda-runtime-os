package net.corda.flow.rest.impl

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.flow.FlowKey
import net.corda.data.flow.output.FlowStatus
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.StartEvent
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.ConfigKeys
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlowStatusLookupServiceImplTest {
    private val subscriptionFactory = mock<SubscriptionFactory>()
    private val lifecycleTestContext = LifecycleTestContext()
    private val lifecycleCoordinator = lifecycleTestContext.lifecycleCoordinator
    private val lifecycleEventRegistration = mock<RegistrationHandle>()
    private val cordaSerializationFactory = mock<CordaAvroSerializationFactory>()
    private val stateManagerFactory = mock<StateManagerFactory>()

    private var eventHandler = mock<LifecycleEventHandler>()
    private val topicSubscription = mock<Subscription<FlowKey, FlowStatus>>()
    private lateinit var flowStatusCacheService: FlowStatusLookupServiceImpl
    private val config = mock<SmartConfig> {
        whenever(it.getInt(INSTANCE_ID)).thenReturn(2)
        whenever(it.getConfig(ConfigKeys.STATE_MANAGER_CONFIG)).thenReturn(mock())
    }

    companion object {
        val FLOW_KEY_1 = FlowKey("a1", HoldingIdentity("b1", "c1"))
        val FLOW_KEY_2 = FlowKey("a2", HoldingIdentity("b1", "c1"))
    }

    @BeforeEach
    fun setup() {
        whenever(lifecycleCoordinator.followStatusChangesByName(any())).thenReturn(lifecycleEventRegistration)

        whenever(subscriptionFactory.createDurableSubscription<FlowKey, FlowStatus>(any(), any(), any(), anyOrNull()))
            .thenReturn(topicSubscription)

        whenever(cordaSerializationFactory.createAvroSerializer<FlowStatus>(any())).thenReturn(mock())

        whenever(stateManagerFactory.create(any(), any())).thenReturn(mock())

        flowStatusCacheService = FlowStatusLookupServiceImpl(
            subscriptionFactory,
            lifecycleTestContext.lifecycleCoordinatorFactory,
            cordaSerializationFactory,
            stateManagerFactory
        )

        eventHandler = lifecycleTestContext.getEventHandler()
    }

    @Test
    fun `Test start starts the lifecycle coordinator`() {
        flowStatusCacheService.start()
        verify(lifecycleCoordinator).start()
    }

    @Test
    fun `Test stop stops the lifecycle coordinator`() {
        flowStatusCacheService.stop()
        verify(lifecycleCoordinator).stop()
    }

    @Test
    fun `Test initialise creates new topic subscription and starts it`() {
        flowStatusCacheService.initialise(config)

        val expectedSubscriptionCfg = SubscriptionConfig(
            "flow_status_subscription",
            Schemas.Flow.FLOW_STATUS_TOPIC
        )

        verify(subscriptionFactory).createDurableSubscription(
            eq(expectedSubscriptionCfg),
            any<DurableFlowStatusProcessor>(),
            same(config),
            same(null)
        )

        verify(topicSubscription).start()
    }

    @Test
    fun `Test initialise closes any existing topic subscription`() {
        flowStatusCacheService.initialise(config)
        // second time around we close the existing subscription
        flowStatusCacheService.initialise(config)
        verify(topicSubscription).close()
    }

    @Test
    fun `Test on start event component status up is signaled`() {
        eventHandler.processEvent(StartEvent(), lifecycleCoordinator)
        verify(lifecycleCoordinator).updateStatus(LifecycleStatus.UP)
    }
}
