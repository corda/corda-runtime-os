package net.corda.flow.application.services

import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.data.flow.state.checkpoint.FlowStackItemSession
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.application.services.impl.FlowEngineImpl
import net.corda.flow.application.versioning.impl.RESET_VERSIONING_MARKER
import net.corda.flow.application.versioning.impl.VERSIONING_PROPERTY_NAME
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.utils.KeyValueStore
import net.corda.flow.utils.mutableKeyValuePairList
import net.corda.session.manager.Constants
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlowEngineImplTest {
    private val flowFiberService = MockFlowFiberService()
    private val flowStack = flowFiberService.flowStack
    private val sandboxDependencyInjector = flowFiberService.flowFiberExecutionContext.sandboxGroupContext.dependencyInjector
    private val flowFiber = flowFiberService.flowFiber

    private val flowStackItem = FlowStackItem.newBuilder()
        .setFlowName("flow-id")
        .setIsInitiatingFlow(true)
        .setSessions(listOf())
        .setContextPlatformProperties(mutableKeyValuePairList())
        .setContextUserProperties(mutableKeyValuePairList())
        .build()
    private val subFlow = mock<SubFlow<String>>()
    private val result = "result"
    private val sessionProperties = KeyValueStore().apply {
        put(Constants.FLOW_SESSION_REQUIRE_CLOSE, false.toString())
    }.avro
    private val errorSessionState =
        SessionState("s", mock(), mock(), null, null, null, SessionStateType.ERROR, false, sessionProperties)
    private val closedSessionState =
        SessionState("s", mock(), mock(), null, null, null, SessionStateType.CLOSED, false, sessionProperties)


    private val flowEngine = FlowEngineImpl(flowFiberService)

    @BeforeEach
    fun setup() {
        whenever(subFlow.call()).thenReturn(result)
        whenever(flowStack.peek()).thenReturn(flowStackItem)
        whenever(flowStack.pop()).thenReturn(flowStackItem)
    }

    @Test
    fun `get virtual node name returns holders x500 name`() {
        val expected = MemberX500Name.parse("CN=Bob, O=Bob Corp, L=LDN, C=GB")
        assertThat(flowEngine.virtualNodeName).isEqualTo(expected)
    }

    @Test
    fun `sub flow with no sessions tied to it completes successfully and does not suspend`() {
        assertThat(flowEngine.subFlow(subFlow)).isEqualTo(result)

        // verify unordered calls.
        verify(sandboxDependencyInjector).injectServices(subFlow)
        verify(flowStack).push(subFlow, flowFiber.getExecutionContext().flowMetrics)

        // verify ordered calls
        inOrder(sandboxDependencyInjector, flowFiber, flowStack, subFlow) {
            verify(sandboxDependencyInjector).injectServices(subFlow)
            verify(subFlow).call()
            verify(flowFiber, never()).suspend(any())
        }
    }

    @Test
    fun `sub flow with uninitiated sessions tied to it completes successfully and does not suspend`() {
        val flowStackItemSession = FlowStackItemSession("s", false)
        flowStackItem.sessions = listOf(flowStackItemSession)
        assertThat(flowEngine.subFlow(subFlow)).isEqualTo(result)

        // verify unordered calls.
        verify(sandboxDependencyInjector).injectServices(subFlow)
        verify(flowStack).push(subFlow, flowFiber.getExecutionContext().flowMetrics)

        // verify ordered calls
        inOrder(sandboxDependencyInjector, flowFiber, flowStack, subFlow) {
            verify(sandboxDependencyInjector).injectServices(subFlow)
            verify(subFlow).call()
            verify(flowFiber, never()).suspend(any())
        }
    }

    @Test
    fun `sub flow with initiated sessions tied to it completes successfully and suspends`() {
        val flowStackItemSession = FlowStackItemSession("s", true)
        flowStackItem.sessions = listOf(flowStackItemSession)
        assertThat(flowEngine.subFlow(subFlow)).isEqualTo(result)

        // verify unordered calls.
        verify(sandboxDependencyInjector).injectServices(subFlow)
        verify(flowStack).push(subFlow, flowFiber.getExecutionContext().flowMetrics)

        // verify ordered calls
        inOrder(sandboxDependencyInjector, flowFiber, flowStack, subFlow) {
            verify(sandboxDependencyInjector).injectServices(subFlow)
            verify(subFlow).call()
            // Assert the flow stack item is popped of the stack
            // and passed to the sub flow finished IO request
            argumentCaptor<FlowIORequest.SubFlowFinished>().apply {
                verify(flowFiber).suspend(capture())

                assertThat(firstValue.sessionIds).containsOnly(flowStackItemSession.sessionId)
            }
        }
    }

    @Test
    fun `sub flow with no sessions tied to it completes with error and does not suspend`() {
        val error = Exception()

        whenever(subFlow.call()).doAnswer { throw error }

        val thrownError = assertThrows<Exception> { flowEngine.subFlow(subFlow) }

        assertThat(thrownError).isEqualTo(error)

        // verify unordered calls.
        verify(sandboxDependencyInjector).injectServices(subFlow)
        verify(flowStack).push(subFlow, flowFiber.getExecutionContext().flowMetrics)

        // verify ordered calls
        inOrder(sandboxDependencyInjector, flowFiber, flowStack, subFlow) {
            verify(sandboxDependencyInjector).injectServices(subFlow)
            verify(subFlow).call()
            verify(flowFiber, never()).suspend(any())
        }
    }

    @Test
    fun `sub flow with uninitiated sessions tied to it completes with error and does not suspend`() {
        val flowStackItemSession = FlowStackItemSession("s", false)
        flowStackItem.sessions = listOf(flowStackItemSession)
        val error = Exception()

        whenever(subFlow.call()).doAnswer { throw error }

        val thrownError = assertThrows<Exception> { flowEngine.subFlow(subFlow) }

        assertThat(thrownError).isEqualTo(error)

        // verify unordered calls.
        verify(sandboxDependencyInjector).injectServices(subFlow)
        verify(flowStack).push(subFlow, flowFiber.getExecutionContext().flowMetrics)

        // verify ordered calls
        inOrder(sandboxDependencyInjector, flowFiber, flowStack, subFlow) {
            verify(sandboxDependencyInjector).injectServices(subFlow)
            verify(subFlow).call()
            verify(flowFiber, never()).suspend(any())
        }
    }

    @Test
    fun `sub flow with initiated sessions tied to it completes with error and suspends`() {
        val flowStackItemSession = FlowStackItemSession("s", true)
        flowStackItem.sessions = listOf(flowStackItemSession)
        val error = Exception()

        whenever(subFlow.call()).doAnswer { throw error }

        val thrownError = assertThrows<Exception> { flowEngine.subFlow(subFlow) }

        assertThat(thrownError).isEqualTo(error)

        // verify unordered calls.
        verify(sandboxDependencyInjector).injectServices(subFlow)
        verify(flowStack).push(subFlow, flowFiber.getExecutionContext().flowMetrics)

        // verify ordered calls
        inOrder(sandboxDependencyInjector, flowFiber, flowStack, subFlow) {
            verify(sandboxDependencyInjector).injectServices(subFlow)
            verify(subFlow).call()

            // Assert the flow stack item and exception are passed
            // to the SubFlowFailed IO request
            argumentCaptor<FlowIORequest.SubFlowFailed>().apply {
                verify(flowFiber).suspend(capture())

                assertThat(firstValue.throwable).isEqualTo(error)
                assertThat(firstValue.sessionIds).isEqualTo(flowStackItem.sessions.map { it.sessionId })
            }
        }
    }

    @Test
    fun `sub flow with error sessions tied to it completes with error and doesn't suspend`() {
        val flowStackItemSession = FlowStackItemSession("s", true)

        flowStackItem.sessions = listOf(flowStackItemSession)

        val error = Exception()

        whenever(subFlow.call()).doAnswer { throw error }

        whenever(
            flowFiberService
                .getExecutingFiber()
                .getExecutionContext().flowCheckpoint.getSessionState("s")
        ).doAnswer { errorSessionState }

        val thrownError = assertThrows<Exception> { flowEngine.subFlow(subFlow) }

        assertThat(thrownError).isEqualTo(error)

        // verify unordered calls.
        verify(sandboxDependencyInjector).injectServices(subFlow)
        verify(flowStack).push(subFlow, flowFiber.getExecutionContext().flowMetrics)

        // verify ordered calls
        inOrder(sandboxDependencyInjector, flowFiber, flowStack, subFlow) {
            verify(sandboxDependencyInjector).injectServices(subFlow)
            verify(subFlow).call()
            verify(flowFiber, never()).suspend(any())
        }
    }

    @Test
    fun `sub flow with closed sessions tied to it completes with error and doesn't suspend`() {
        val flowStackItemSession = FlowStackItemSession("s", true)

        flowStackItem.sessions = listOf(flowStackItemSession)

        val error = Exception()

        whenever(subFlow.call()).doAnswer { throw error }

        whenever(
            flowFiberService
                .getExecutingFiber()
                .getExecutionContext()
                .flowCheckpoint.getSessionState("s")
        ).doAnswer { closedSessionState }

        val thrownError = assertThrows<Exception> { flowEngine.subFlow(subFlow) }

        assertThat(thrownError).isEqualTo(error)

        // verify unordered calls.
        verify(sandboxDependencyInjector).injectServices(subFlow)
        verify(flowStack).push(subFlow, flowFiber.getExecutionContext().flowMetrics)

        // verify ordered calls
        inOrder(sandboxDependencyInjector, flowFiber, flowStack, subFlow) {
            verify(sandboxDependencyInjector).injectServices(subFlow)
            verify(subFlow).call()
            verify(flowFiber, never()).suspend(any())
        }
    }

    @Test
    fun `sub flow with error sessions tied to it completes successfully and doesn't suspend`() {
        val flowStackItemSession = FlowStackItemSession("s", true)
        flowStackItem.sessions = listOf(flowStackItemSession)

        whenever(
            flowFiberService
                .getExecutingFiber()
                .getExecutionContext().flowCheckpoint.getSessionState("s")
        ).doAnswer { errorSessionState }

        assertThat(flowEngine.subFlow(subFlow)).isEqualTo(result)

        // verify unordered calls.
        verify(sandboxDependencyInjector).injectServices(subFlow)
        verify(flowStack).push(subFlow, flowFiber.getExecutionContext().flowMetrics)

        // verify ordered calls
        inOrder(sandboxDependencyInjector, flowFiber, flowStack, subFlow) {
            verify(sandboxDependencyInjector).injectServices(subFlow)
            verify(subFlow).call()
            verify(flowFiber, never()).suspend(any())
        }
    }

    @Test
    fun `sub flow with closed sessions tied to it completes successfully and doesn't suspend`() {
        val flowStackItemSession = FlowStackItemSession("s", true)
        flowStackItem.sessions = listOf(flowStackItemSession)

        whenever(
            flowFiberService
                .getExecutingFiber()
                .getExecutionContext()
                .flowCheckpoint.getSessionState("s")
        ).doAnswer { closedSessionState }

        assertThat(flowEngine.subFlow(subFlow)).isEqualTo(result)

        // verify unordered calls.
        verify(sandboxDependencyInjector).injectServices(subFlow)
        verify(flowStack).push(subFlow, flowFiber.getExecutionContext().flowMetrics)

        // verify ordered calls
        inOrder(sandboxDependencyInjector, flowFiber, flowStack, subFlow) {
            verify(sandboxDependencyInjector).injectServices(subFlow)
            verify(subFlow).call()
            verify(flowFiber, never()).suspend(any())
        }
    }

    @Test
    fun `resets versioning information if the subFlow is an initiating flow`() {
        val flowStackItem = FlowStackItem.newBuilder()
            .setFlowName("flow-id")
            .setIsInitiatingFlow(true)
            .setSessions(listOf())
            .setContextPlatformProperties(mutableKeyValuePairList())
            .setContextUserProperties(mutableKeyValuePairList())
            .build()
        whenever(flowStack.peek()).thenReturn(flowStackItem)
        whenever(flowFiber.getExecutionContext().flowCheckpoint.flowContext.get(VERSIONING_PROPERTY_NAME)).thenReturn(1.toString())
        flowEngine.subFlow(subFlow)
        verify(flowFiber.getExecutionContext().flowCheckpoint.flowContext.platformProperties)[VERSIONING_PROPERTY_NAME] =
            RESET_VERSIONING_MARKER
    }

    @Test
    fun `does not reset versioning information if the subFlow is not an initiating flow`() {
        val flowStackItem = FlowStackItem.newBuilder()
            .setFlowName("flow-id")
            .setIsInitiatingFlow(false)
            .setSessions(listOf())
            .setContextPlatformProperties(mutableKeyValuePairList())
            .setContextUserProperties(mutableKeyValuePairList())
            .build()
        whenever(flowStack.peek()).thenReturn(flowStackItem)
        whenever(flowFiber.getExecutionContext().flowCheckpoint.flowContext.get(VERSIONING_PROPERTY_NAME)).thenReturn(null)
        flowEngine.subFlow(subFlow)
        verify(flowFiber.getExecutionContext().flowCheckpoint.flowContext.platformProperties, never())[VERSIONING_PROPERTY_NAME] =
            RESET_VERSIONING_MARKER
    }
}