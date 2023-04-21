package net.corda.flow.service

import com.typesafe.config.ConfigValueFactory
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.pipeline.factory.FlowEventProcessorFactory
import net.corda.flow.scheduler.FlowWakeUpScheduler
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys.FLOW_CONFIG
import net.corda.schema.configuration.MessagingConfig.MAX_ALLOWED_MSG_SIZE
import net.corda.schema.configuration.MessagingConfig.Subscription.PROCESSOR_TIMEOUT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.inOrder
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlowExecutorImplTest {

    private val coordinatorFactory = mock<LifecycleCoordinatorFactory>()
    private val flowEventProcessorFactory = mock<FlowEventProcessorFactory>()
    private val subscriptionFactory = mock<SubscriptionFactory>()
    private val flowWakeUpScheduler = mock<FlowWakeUpScheduler>()
    private val toMessagingConfig: (Map<String, SmartConfig>) -> SmartConfig = {
        messagingConfig
    }

    private val config = mutableMapOf(
        FLOW_CONFIG to SmartConfigImpl.empty()
    )
    private val messagingConfig = getMinimalMessagingConfig()
    private val subscriptionRegistrationHandle = mock<RegistrationHandle>()
    private val flowExecutorCoordinator = mock<LifecycleCoordinator>()
    private val subscription = mock<StateAndEventSubscription<String, Checkpoint, FlowEvent>>()
    private val flowEventProcessor = mock<StateAndEventProcessor<String, Checkpoint, FlowEvent>>()

    @BeforeEach
    fun setup() {
        whenever(flowEventProcessorFactory.create(any())).thenReturn(flowEventProcessor)
        whenever(
            subscriptionFactory.createStateAndEventSubscription<String, Checkpoint, FlowEvent>(
                any(),
                any(),
                any(),
                any()
            )
        ).thenReturn(subscription)

        whenever(coordinatorFactory.createCoordinator(any(), any())).thenReturn(flowExecutorCoordinator)
        whenever(flowExecutorCoordinator.followStatusChangesByName(any())).thenReturn(subscriptionRegistrationHandle)
    }

    @Test
    fun `lifecycle - flow executor signals up when it is started`() {
        getFlowExecutor().start()

        verify(flowExecutorCoordinator).start()
    }

    @Test
    fun `lifecycle - flow executor signals error if it fails to create a subscription`() {
        val invalidConfig = mapOf<String, SmartConfig>()

        val flowExecutor = getFlowExecutor()
        flowExecutor.start()
        flowExecutor.onConfigChange(invalidConfig)
        verify(flowExecutorCoordinator).updateStatus(
            LifecycleStatus.ERROR,
            "Failed to configure the flow executor using '{}'"
        )
    }

    @Test
    fun `lifecycle - flow executor signals error if the subscription signals error`() {
        val name = LifecycleCoordinatorName("", "")
        whenever(subscription.subscriptionName).thenReturn(name)

        val flowExecutor = getFlowExecutor()
        flowExecutor.start()
        flowExecutor.onConfigChange(config)

        verify(flowExecutorCoordinator).followStatusChangesByName(eq(setOf(name)))
    }

    @Test
    fun `lifecycle - flow executor signals down when stopped`() {
        getFlowExecutor().stop()

        verify(flowExecutorCoordinator).stop()
    }

    @Test
    fun `lifecycle - flow executor stops subscription when stopped`() {
        val flowExecutor = getFlowExecutor()
        flowExecutor.onConfigChange(config)

        // Fire the events
        argumentCaptor<LifecycleEventHandler>().apply {
            verify(coordinatorFactory).createCoordinator(any(), capture())
            firstValue.processEvent(StopEvent(), flowExecutorCoordinator)
        }

        verify(subscriptionRegistrationHandle).close()
        verify(subscription).close()
    }

    @Test
    fun `lifecycle - flow executor does not signal lifecycle change for successful reconfiguration`() {
        val name1 = LifecycleCoordinatorName("", "")
        val name2 = LifecycleCoordinatorName("", "")
        val subscriptionRegistrationHandle2 = mock<RegistrationHandle>()
        val subscription2 = mock<StateAndEventSubscription<String, Checkpoint, FlowEvent>>()

        whenever(subscription.subscriptionName).thenReturn(name1)
        whenever(subscription2.subscriptionName).thenReturn(name2)

        // First config change gets us subscribed
        val flowExecutor = getFlowExecutor()
        flowExecutor.start()
        flowExecutor.onConfigChange(config)

        // now we change config and should see the subscription registration removed,
        // the subscription re-created and then the subscription registered again
        whenever(
            subscriptionFactory.createStateAndEventSubscription<String, Checkpoint, FlowEvent>(
                any(),
                any(),
                any(),
                any()
            )
        ).thenReturn(subscription2)

        whenever(flowExecutorCoordinator.followStatusChangesByName(any())).thenReturn(subscriptionRegistrationHandle2)

        flowExecutor.onConfigChange(config)

        inOrder(
            subscription,
            subscription2,
            subscriptionRegistrationHandle,
            subscriptionRegistrationHandle2,
            flowExecutorCoordinator
        ).apply {
            verify(subscriptionRegistrationHandle).close()
            verify(subscription).close()
            verify(flowExecutorCoordinator).followStatusChangesByName(eq(setOf(name2)))
            verify(subscription2).start()
        }
    }

    @Test
    fun `lifecycle - flow executor is running when the coordinator is running`() {
        whenever(flowExecutorCoordinator.isRunning).thenReturn(true)
        assertThat(getFlowExecutor().isRunning)
    }

    private fun getFlowExecutor(): FlowExecutorImpl {
        return FlowExecutorImpl(
            coordinatorFactory,
            subscriptionFactory,
            flowEventProcessorFactory,
            flowWakeUpScheduler,
            toMessagingConfig
        )
    }

    private fun getMinimalMessagingConfig() : SmartConfig {
        return SmartConfigImpl.empty()
            .withValue(PROCESSOR_TIMEOUT, ConfigValueFactory.fromAnyRef(5000))
            .withValue(MAX_ALLOWED_MSG_SIZE, ConfigValueFactory.fromAnyRef(1000000000))
    }
}
