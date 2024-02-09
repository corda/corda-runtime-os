package net.corda.flow.maintenance

import net.corda.data.flow.FlowCheckpointTermination
import net.corda.data.flow.FlowTimeout
import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.Resource
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.StateManagerConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlowMaintenanceImplTests {
    private val stateManager = mock<StateManager> {
        on { name } doReturn (LifecycleCoordinatorName("MockManager", "MockId"))
    }

    private val stateManagerFactory = mock<StateManagerFactory> {
        on { create(any(), eq(StateManagerConfig.StateType.FLOW_CHECKPOINT)) } doReturn (stateManager)
    }
    private val timeoutSubscription = mock<Subscription<String, FlowTimeout>>()
    private val scheduledTaskSubscription = mock<Subscription<String, ScheduledTaskTrigger>>()
    private val checkpointDeletionTaskSubscription = mock<Subscription<String, ScheduledTaskTrigger>>()
    private val checkpointDeletionExecutorSubscription = mock<Subscription<String, FlowCheckpointTermination>>()

    private val lifecycleCoordinator = mock<LifecycleCoordinator> {
        val captor = argumentCaptor<() -> Resource>()
        on { createManagedResource(any(), captor.capture()) } doAnswer { captor.lastValue.invoke() }
    }
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) } doReturn (lifecycleCoordinator)
    }

    private val subscriptionFactory = mock<SubscriptionFactory> {
        on {
            createDurableSubscription(any(), any<FlowTimeoutTaskProcessor>(), any(), anyOrNull())
        } doReturn (scheduledTaskSubscription)
        on {
            createDurableSubscription(any(), any<TimeoutEventCleanupProcessor>(), any(), anyOrNull())
        } doReturn (timeoutSubscription)
        on {
            createDurableSubscription(any(), any<FlowCheckpointTerminationTaskProcessor>(), any(), anyOrNull())
        } doReturn (checkpointDeletionTaskSubscription)
        on {
            createDurableSubscription(any(), any<FlowCheckpointTerminationCleanupProcessor>(), any(), anyOrNull())
        } doReturn (checkpointDeletionExecutorSubscription)
    }

    private val messagingConfig = mock<SmartConfig>().apply {
        whenever(getLong(any())).thenReturn(100L)
    }
    private val stateManagerConfig = mock<SmartConfig>()
    private val flowConfig = mock<SmartConfig>().apply {
        whenever(getLong(any())).thenReturn(10L)
        whenever(withValue(any(), any())).thenReturn(this)
    }

    private val config = mapOf(
        ConfigKeys.FLOW_CONFIG to flowConfig,
        ConfigKeys.MESSAGING_CONFIG to messagingConfig,
        ConfigKeys.STATE_MANAGER_CONFIG to stateManagerConfig,
    )

    private val flowMaintenanceHandlersFactory = mock<FlowMaintenanceHandlersFactory>()

    private val flowMaintenance = FlowMaintenanceImpl(
        lifecycleCoordinatorFactory,
        subscriptionFactory,
        stateManagerFactory,
        flowMaintenanceHandlersFactory
    )

    @BeforeEach
    fun setUp() {
        doReturn(FlowTimeoutTaskProcessor(stateManager, flowConfig))
            .whenever(flowMaintenanceHandlersFactory).createScheduledTaskHandler(any(), any())

        doReturn(TimeoutEventCleanupProcessor(mock(), stateManager, mock(), mock(), flowConfig))
            .whenever(flowMaintenanceHandlersFactory).createTimeoutEventHandler(any(), any())
    }

    @Test
    fun `when config provided create subscription and start it`() {
        flowMaintenance.onConfigChange(config)

        verify(lifecycleCoordinator, times(4)).createManagedResource(any(), any<() -> Resource>())
        verify(subscriptionFactory, times(1)).createDurableSubscription(
            argThat { it ->
                it.eventTopic == Schemas.ScheduledTask.SCHEDULED_TASK_TOPIC_FLOW_PROCESSOR
            },
            any<FlowTimeoutTaskProcessor>(),
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
        verify(stateManagerFactory).create(eq(stateManagerConfig), eq(StateManagerConfig.StateType.FLOW_CHECKPOINT))
        verify(stateManager).start()
        verify(scheduledTaskSubscription).start()
        verify(timeoutSubscription).start()
        verify(lifecycleCoordinator).followStatusChangesByName(setOf(stateManager.name))
    }

    @Test
    fun `when no state manager config pushed and messaging config pushed do not create another StateManager`() {
        flowMaintenance.onConfigChange(config)
        flowMaintenance.onConfigChange(mapOf(ConfigKeys.MESSAGING_CONFIG to messagingConfig))

        verify(stateManagerFactory, Times(1))
            .create(eq(stateManagerConfig), eq(StateManagerConfig.StateType.FLOW_CHECKPOINT))
    }

    @Test
    fun `when no messaging config pushed and no state manager config pushed do not create another StateManager`() {
        flowMaintenance.onConfigChange(config)
        flowMaintenance.onConfigChange(mapOf(ConfigKeys.STATE_MANAGER_CONFIG to mock<SmartConfig>()))

        verify(stateManagerFactory, Times(1))
            .create(eq(stateManagerConfig), eq(StateManagerConfig.StateType.FLOW_CHECKPOINT))
    }

    @Test
    fun `when new state manager config pushed create another StateManager and close old`() {
        flowMaintenance.onConfigChange(config)
        val newConfig = mock<SmartConfig>().apply { whenever(withValue(any(), any())).thenReturn(this) }
        val newStateManager = mock<StateManager> {
            on { name } doReturn (LifecycleCoordinatorName("StateManager", "2"))
        }
        whenever(stateManagerFactory.create(eq(newConfig), eq(StateManagerConfig.StateType.FLOW_CHECKPOINT)))
            .thenReturn(newStateManager)

        flowMaintenance.onConfigChange(
            mapOf(
                ConfigKeys.MESSAGING_CONFIG to newConfig,
                ConfigKeys.STATE_MANAGER_CONFIG to newConfig,
                ConfigKeys.FLOW_CONFIG to newConfig
            )
        )

        verify(stateManagerFactory).create(eq(newConfig), eq(StateManagerConfig.StateType.FLOW_CHECKPOINT))
        verify(stateManager).stop()
        verify(lifecycleCoordinator).followStatusChangesByName(setOf(newStateManager.name))
        verify(lifecycleCoordinator, times(8)).createManagedResource(any(), any<() -> Resource>())
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
