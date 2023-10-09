package net.corda.flow.maintenance

import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class FlowMaintenanceImplTests {
    private val subscription = mock<Subscription<String, ScheduledTaskTrigger>>()
    private val lifecycleCoordinator = mock<LifecycleCoordinator> {
        on { createManagedResource(any(), any<() -> Subscription<String, ScheduledTaskTrigger>>()) } doReturn (subscription)
    }
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) } doReturn (lifecycleCoordinator)
    }
    private val subscriptionFactory = mock<SubscriptionFactory> {
        on { createDurableSubscription(any(), any<SessionTimeoutTaskProcessor>(), any(), any()) } doReturn(subscription)
    }
    private val stateManager = mock<StateManager>()
    private val stateManagerFactory = mock<StateManagerFactory> {
        on { create(any()) } doReturn (stateManager)
    }
    private val messagingConfig = mock<SmartConfig>()
    // TODO - fix this when state manager config is split up from messaging
    private val stateManagerConfig = messagingConfig
    private val config = mapOf(
        ConfigKeys.MESSAGING_CONFIG to messagingConfig
    )

    @Test
    fun `when config provided create subscription and start it`() {
        val captor = argumentCaptor<() -> Subscription<String, ScheduledTaskTrigger>>()
        val m = FlowMaintenanceImpl(lifecycleCoordinatorFactory, subscriptionFactory, stateManagerFactory)
        m.onConfigChange(config)
        verify(lifecycleCoordinator).createManagedResource(any(), captor.capture())
        captor.firstValue()
        verify(subscriptionFactory).createDurableSubscription(
            argThat { it ->
                it.eventTopic == Schemas.ScheduledTask.SCHEDULED_TASK_TOPIC_FLOW_PROCESSOR
            },
            any<SessionTimeoutTaskProcessor>(),
            eq(messagingConfig),
            isNull()
        )
        verify(stateManagerFactory).create(stateManagerConfig)
        verify(subscription).start()
    }

    @Test
    fun `when same state manager config pushed do not create another StateManager`() {
        val m = FlowMaintenanceImpl(lifecycleCoordinatorFactory, subscriptionFactory, stateManagerFactory)
        m.onConfigChange(config)
        m.onConfigChange(config)
        verify(stateManagerFactory, Times(1)).create(stateManagerConfig)
    }

    @Test
    fun `when new state manager config pushed create another StateManager and close old`() {
        val m = FlowMaintenanceImpl(lifecycleCoordinatorFactory, subscriptionFactory, stateManagerFactory)
        m.onConfigChange(config)
        val newConfig = mock<SmartConfig>()
        m.onConfigChange(mapOf(ConfigKeys.MESSAGING_CONFIG to newConfig))
        verify(stateManagerFactory).create(newConfig)
        verify(stateManager).close()
    }

    @Test
    fun `when stop close StateManager`() {
        val m = FlowMaintenanceImpl(lifecycleCoordinatorFactory, subscriptionFactory, stateManagerFactory)
        m.onConfigChange(config)
        m.stop()
        verify(stateManager).close()
    }

    @Test
    fun `do nothing when messaging config not sent`() {
        val m = FlowMaintenanceImpl(lifecycleCoordinatorFactory, subscriptionFactory, stateManagerFactory)
        m.onConfigChange(mapOf("foo" to mock()))
        verify(lifecycleCoordinator, never()).createManagedResource(any(), any<() -> Subscription<String, ScheduledTaskTrigger>>())
    }
}