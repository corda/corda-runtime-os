package net.corda.flow.service

import com.typesafe.config.ConfigValueFactory
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.messaging.mediator.FlowEventMediatorFactory
import net.corda.flow.pipeline.factory.FlowEventProcessorFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
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
import net.corda.schema.configuration.ConfigKeys.STATE_MANAGER_CONFIG
import net.corda.schema.configuration.MessagingConfig.MAX_ALLOWED_MSG_SIZE
import net.corda.schema.configuration.MessagingConfig.Subscription.PROCESSOR_TIMEOUT
import net.corda.schema.configuration.StateManagerConfig
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
    private val stateManagerFactory = mock<StateManagerFactory>()
    private val flowEventMediatorFactory = mock<FlowEventMediatorFactory>()
    private val toMessagingConfig: (Map<String, SmartConfig>) -> SmartConfig = {
        messagingConfig
    }

    private val config = mutableMapOf(
        BOOT_CONFIG to SmartConfigImpl.empty().withServiceEndpoints(),
        FLOW_CONFIG to SmartConfigImpl.empty(),
        STATE_MANAGER_CONFIG to SmartConfigImpl.empty(),
    )
    private val messagingConfig = getMinimalMessagingConfig()
    private val subscriptionRegistrationHandle = mock<RegistrationHandle>()
    private val flowExecutorCoordinator = mock<LifecycleCoordinator>()
    private val multiSourceEventMediator = mock<MultiSourceEventMediator<String, Checkpoint, FlowEvent>>()
    private val flowEventProcessor = mock<StateAndEventProcessor<String, Checkpoint, FlowEvent>>()
    private val stateManager = mock<StateManager>()

    @BeforeEach
    fun setup() {
        whenever(flowEventProcessorFactory.create(any())).thenReturn(flowEventProcessor)
        whenever(stateManagerFactory.create(any(), eq(StateManagerConfig.StateType.FLOW_CHECKPOINT))).thenReturn(stateManager)
        whenever(
            flowEventMediatorFactory.create(
                any(),
                any(),
                any(),
                any()
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
    fun `lifecycle - flow executor follows event mediator and state manager lifecycle events`() {
        val subscriptionName = LifecycleCoordinatorName("mediator", "1")
        whenever(multiSourceEventMediator.subscriptionName).thenReturn(subscriptionName)
        val stateManagerName = LifecycleCoordinatorName("stateManager", "1")
        whenever(stateManager.name).thenReturn(stateManagerName)

        val flowExecutor = getFlowExecutor()
        flowExecutor.start()
        flowExecutor.onConfigChange(config)

        verify(flowExecutorCoordinator).followStatusChangesByName(eq(setOf(subscriptionName, stateManagerName)))
    }

    @Test
    fun `lifecycle - flow executor signals down when stopped`() {
        getFlowExecutor().stop()

        verify(flowExecutorCoordinator).stop()
    }

    @Test
    fun `lifecycle - flow executor stops event mediator and state manager when stopped`() {
        val flowExecutor = getFlowExecutor()
        flowExecutor.onConfigChange(config)

        // Fire the events
        argumentCaptor<LifecycleEventHandler>().apply {
            verify(coordinatorFactory).createCoordinator(any(), capture())
            firstValue.processEvent(StopEvent(), flowExecutorCoordinator)
        }

        verify(subscriptionRegistrationHandle).close()
        verify(multiSourceEventMediator).close()
        verify(stateManager).stop()
    }

    @Test
    fun `lifecycle - flow executor does not signal lifecycle change for successful reconfiguration`() {
        val subscriptionName1 = LifecycleCoordinatorName("mediator", "1")
        val stateManagerName1 = LifecycleCoordinatorName("stateManager", "1")
        val subscriptionName2 = LifecycleCoordinatorName("mediator", "2")
        val stateManagerName2 = LifecycleCoordinatorName("stateManager", "2")

        val stateManager2 = mock<StateManager>()
        val subscriptionRegistrationHandle2 = mock<RegistrationHandle>()
        val multiSourceEventMediator2 = mock<MultiSourceEventMediator<String, Checkpoint, FlowEvent>>()

        whenever(stateManager.name).thenReturn(stateManagerName1)
        whenever(multiSourceEventMediator.subscriptionName).thenReturn(subscriptionName1)
        whenever(stateManager2.name).thenReturn(stateManagerName2)
        whenever(multiSourceEventMediator2.subscriptionName).thenReturn(subscriptionName2)

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
                any(),
                any()
            )
        ).thenReturn(multiSourceEventMediator2)
        whenever(stateManagerFactory.create(any(), eq(StateManagerConfig.StateType.FLOW_CHECKPOINT))).thenReturn(stateManager2)
        whenever(flowExecutorCoordinator.followStatusChangesByName(any())).thenReturn(subscriptionRegistrationHandle2)

        flowExecutor.onConfigChange(config)

        inOrder(
            multiSourceEventMediator,
            multiSourceEventMediator2,
            stateManager, stateManager2,
            subscriptionRegistrationHandle,
            subscriptionRegistrationHandle2,
            flowExecutorCoordinator
        ).apply {
            verify(subscriptionRegistrationHandle).close()
            verify(multiSourceEventMediator).close()
            verify(stateManager).stop()
            verify(flowExecutorCoordinator).followStatusChangesByName(eq(setOf(subscriptionName2, stateManagerName2)))
            verify(stateManager2).start()
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
            stateManagerFactory,
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
            .withEndpoint(BootConfig.TOKEN_SELECTION_WORKER_REST_ENDPOINT, "TEST_TOKEN_SELECTION_ENDPOINT")
    }
}
