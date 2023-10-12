package net.corda.flow.service

import com.typesafe.config.ConfigValueFactory
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.messaging.mediator.FlowEventMediatorFactory
import net.corda.flow.pipeline.factory.FlowEventProcessorFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.mediator.MultiSourceEventMediator
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
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
    private val flowEventMediatorFactory = mock<FlowEventMediatorFactory>()
    private val toMessagingConfig: (Map<String, SmartConfig>) -> SmartConfig = {
        messagingConfig
    }

    private val config = mutableMapOf(
        BOOT_CONFIG to SmartConfigImpl.empty().withServiceEndpoints(),
        FLOW_CONFIG to SmartConfigImpl.empty()
    )
    private val messagingConfig = getMinimalMessagingConfig()
    private val subscriptionRegistrationHandle = mock<RegistrationHandle>()
    private val flowExecutorCoordinator = mock<LifecycleCoordinator>()
    private val multiSourceEventMediator = mock<MultiSourceEventMediator<String, Checkpoint, FlowEvent>>()
    private val flowEventProcessor = mock<StateAndEventProcessor<String, Checkpoint, FlowEvent>>()

    @BeforeEach
    fun setup() {
        whenever(flowEventProcessorFactory.create(any())).thenReturn(flowEventProcessor)
        whenever(
            flowEventMediatorFactory.create(
                any(),
                any(),
            )
        ).thenReturn(multiSourceEventMediator)

        whenever(coordinatorFactory.createCoordinator(any(), any())).thenReturn(flowExecutorCoordinator)
        whenever(flowExecutorCoordinator.followStatusChangesByName(any())).thenReturn(subscriptionRegistrationHandle)
    }

    @Test
    fun `lifecycle - flow executor signals up when it is started`() {
        getFlowExecutor().start()

        verify(flowExecutorCoordinator).start()
    }

    @Test
    fun `lifecycle - flow executor signals error if it fails to create event mediator`() {
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
    fun `lifecycle - flow executor signals error if event mediator signals error`() {
        val name = LifecycleCoordinatorName("", "")
        whenever(multiSourceEventMediator.subscriptionName).thenReturn(name)

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
    fun `lifecycle - flow executor stops event mediator when stopped`() {
        val flowExecutor = getFlowExecutor()
        flowExecutor.onConfigChange(config)

        // Fire the events
        argumentCaptor<LifecycleEventHandler>().apply {
            verify(coordinatorFactory).createCoordinator(any(), capture())
            firstValue.processEvent(StopEvent(), flowExecutorCoordinator)
        }

        verify(subscriptionRegistrationHandle).close()
        verify(multiSourceEventMediator).close()
    }

    @Test
    fun `lifecycle - flow executor does not signal lifecycle change for successful reconfiguration`() {
        val name1 = LifecycleCoordinatorName("", "")
        val name2 = LifecycleCoordinatorName("", "")
        val subscriptionRegistrationHandle2 = mock<RegistrationHandle>()
        val multiSourceEventMediator2 = mock<MultiSourceEventMediator<String, Checkpoint, FlowEvent>>()

        whenever(multiSourceEventMediator.subscriptionName).thenReturn(name1)
        whenever(multiSourceEventMediator2.subscriptionName).thenReturn(name2)

        // First config change gets us subscribed
        val flowExecutor = getFlowExecutor()
        flowExecutor.start()
        flowExecutor.onConfigChange(config)

        // now we change config and should see the subscription registration removed,
        // the subscription re-created and then the subscription registered again
        whenever(
            flowEventMediatorFactory.create(
                any(),
                any(),
            )
        ).thenReturn(multiSourceEventMediator2)

        whenever(flowExecutorCoordinator.followStatusChangesByName(any())).thenReturn(subscriptionRegistrationHandle2)

        flowExecutor.onConfigChange(config)

        inOrder(
            multiSourceEventMediator,
            multiSourceEventMediator2,
            subscriptionRegistrationHandle,
            subscriptionRegistrationHandle2,
            flowExecutorCoordinator
        ).apply {
            verify(subscriptionRegistrationHandle).close()
            verify(multiSourceEventMediator).close()
            verify(flowExecutorCoordinator).followStatusChangesByName(eq(setOf(name2)))
            verify(multiSourceEventMediator2).start()
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
            flowEventMediatorFactory,
            toMessagingConfig
        )
    }

    private fun getMinimalMessagingConfig(): SmartConfig {
        return SmartConfigImpl.empty()
            .withValue(PROCESSOR_TIMEOUT, ConfigValueFactory.fromAnyRef(5000))
            .withValue(MAX_ALLOWED_MSG_SIZE, ConfigValueFactory.fromAnyRef(1000000000))
    }

    private fun SmartConfig.withServiceEndpoints(): SmartConfig {
        fun SmartConfig.withEndpoint(endpoint: String, value: String): SmartConfig {
            return withValue(endpoint, ConfigValueFactory.fromAnyRef(value))
        }

        return this
            .withEndpoint(BootConfig.CRYPTO_WORKER_REST_ENDPOINT, "TEST_CRYPTO_ENDPOINT")
            .withEndpoint(BootConfig.PERSISTENCE_WORKER_REST_ENDPOINT, "TEST_PERSISTENCE_ENDPOINT")
            .withEndpoint(BootConfig.UNIQUENESS_WORKER_REST_ENDPOINT, "TEST_UNIQUENESS_ENDPOINT")
            .withEndpoint(BootConfig.VERIFICATION_WORKER_REST_ENDPOINT, "TEST_VERIFICATION_ENDPOINT")
    }
}
