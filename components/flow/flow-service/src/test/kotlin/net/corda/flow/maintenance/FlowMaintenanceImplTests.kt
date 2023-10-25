package net.corda.flow.maintenance

import net.corda.data.flow.FlowTimeout
import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.Resource
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
import org.mockito.kotlin.*

class FlowMaintenanceImplTests {

    private val stateManager = mock<StateManager>()
    private val stateManagerFactory = mock<StateManagerFactory> {
        on { create(any()) } doReturn (stateManager)
    }

    private val subscription = mock<Subscription<String, ScheduledTaskTrigger>>()
    private val timeoutSubscription = mock<Subscription<String, FlowTimeout>>()
    private val lifecycleCoordinator = mock<LifecycleCoordinator> {
        val captor = argumentCaptor<() -> Resource>()
        on { createManagedResource(any(), captor.capture()) } doAnswer { captor.lastValue.invoke() }
    }
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) } doReturn (lifecycleCoordinator)
    }
    private val subscriptionFactory = mock<SubscriptionFactory> {
        on { createDurableSubscription(any(), any<SessionTimeoutTaskProcessor>(), any(), anyOrNull()) } doReturn(subscription)
        on { createDurableSubscription(any(), any<TimeoutEventCleanupProcessor>(), any(), anyOrNull()) } doReturn (timeoutSubscription)
    }

    private val messagingConfig = mock<SmartConfig>().apply {
        whenever(getLong(any())).thenReturn(100L)
    }
    private val stateManagerConfig = mock<SmartConfig>()
    private val flowConfig = mock<SmartConfig>().apply {
        whenever(withValue(any(), any())).thenReturn(this)
    }

    private val config = mapOf(
        ConfigKeys.MESSAGING_CONFIG to messagingConfig,
        ConfigKeys.STATE_MANAGER_CONFIG to stateManagerConfig,
        ConfigKeys.FLOW_CONFIG to flowConfig
    )

    private val flowMaintenanceHandlersFactory = mock<FlowMaintenanceHandlersFactory> {
        on { createScheduledTaskHandler(any()) } doReturn (SessionTimeoutTaskProcessor(stateManager))
        on { createTimeoutEventHandler(any(), any()) } doReturn (
            TimeoutEventCleanupProcessor(mock(), stateManager, mock(), mock(), flowConfig)
        )
    }

    private val flowMaintenance = FlowMaintenanceImpl(
        lifecycleCoordinatorFactory,
        subscriptionFactory,
        stateManagerFactory,
        flowMaintenanceHandlersFactory
    )

    @Test
    fun `when config provided create subscription and start it`() {
        flowMaintenance.onConfigChange(config)
        verify(lifecycleCoordinator, times(3)).createManagedResource(any(), any<() -> Resource>())
        verify(subscriptionFactory, times(1)).createDurableSubscription(
            argThat { it ->
                it.eventTopic == Schemas.ScheduledTask.SCHEDULED_TASK_TOPIC_FLOW_PROCESSOR
            },
            any<SessionTimeoutTaskProcessor>(),
            eq(messagingConfig),
            isNull()
        )
        verify(subscriptionFactory, times(1)).createDurableSubscription(
            argThat { it ->
                it.eventTopic == Schemas.Flow.FLOW_TIMEOUT_TOPIC
            },
            any<TimeoutEventCleanupProcessor>(),
            eq(messagingConfig),
            isNull()
        )
        verify(stateManagerFactory).create(stateManagerConfig)
        verify(subscription).start()
        verify(timeoutSubscription).start()
    }

    @Test
    fun `when no state manager config pushed and messaging config pushed do not create another StateManager`() {
        flowMaintenance.onConfigChange(config)
        flowMaintenance.onConfigChange(mapOf(ConfigKeys.MESSAGING_CONFIG to messagingConfig))

        verify(stateManagerFactory, Times(1)).create(stateManagerConfig)
    }

    @Test
    fun `when no messaging config pushed and no state manager config pushed do not create another StateManager`() {
        flowMaintenance.onConfigChange(config)
        flowMaintenance.onConfigChange(mapOf(ConfigKeys.STATE_MANAGER_CONFIG to mock<SmartConfig>()))

        verify(stateManagerFactory, Times(1)).create(stateManagerConfig)
    }

    @Test
    fun `when new state manager config pushed create another StateManager and close old`() {
        flowMaintenance.onConfigChange(config)
        val newConfig = mock<SmartConfig>().apply {
            whenever(withValue(any(), any())).thenReturn(this)
        }
        flowMaintenance.onConfigChange(
            mapOf(
                ConfigKeys.MESSAGING_CONFIG to newConfig,
                ConfigKeys.STATE_MANAGER_CONFIG to newConfig,
                ConfigKeys.FLOW_CONFIG to newConfig
            )
        )

        verify(stateManagerFactory).create(newConfig)
        verify(lifecycleCoordinator, times(6)).createManagedResource(any(), any<() -> Resource>())
    }

    @Test
    fun `when stop close StateManager`() {
        flowMaintenance.onConfigChange(config)
        flowMaintenance.stop()

        verify(lifecycleCoordinator).stop()
    }

    @Test
    fun `do nothing when messaging config not sent`() {
        flowMaintenance.onConfigChange(mapOf("foo" to mock()))

        verify(lifecycleCoordinator, never()).createManagedResource(any(), any<() -> Subscription<String, ScheduledTaskTrigger>>())
    }
}
