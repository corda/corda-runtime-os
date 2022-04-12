package net.corda.flow.application.services

import net.corda.data.flow.FlowStackItem
import net.corda.flow.fiber.FlowIORequest
import net.corda.v5.application.flows.Flow
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlowEngineImplTest {
    private val flowFiberService = MockFlowFiberService()
    private val flowStackService = flowFiberService.flowFiberExecutionContext.flowStackService
    private val sandboxDependencyInjector = flowFiberService.flowFiberExecutionContext.sandboxDependencyInjector
    private val flowFiber = flowFiberService.flowFiber
    private val flowStackItem = FlowStackItem()
    private val subFlow = mock<Flow<String>>()
    private val result = "result"

    @BeforeEach
    fun setup() {
        whenever(subFlow.call()).thenReturn(result)
        whenever(flowStackService.peek()).thenReturn(flowStackItem)
        whenever(flowStackService.pop()).thenReturn(flowStackItem)
    }

    @Test
    fun `get virtual node name returns holders x500 name`() {
        val flowEngine = FlowEngineImpl(flowFiberService)
        val expected = MemberX500Name.parse("CN=Bob, O=Bob Corp, L=LDN, C=GB")
        assertThat(flowEngine.virtualNodeName).isEqualTo(expected)
    }

    @Test
    fun `sub flow completes successfully`() {
        val flowEngine = FlowEngineImpl(flowFiberService)

        assertThat(flowEngine.subFlow(subFlow)).isEqualTo(result)

        // verify unordered calls
        verify(sandboxDependencyInjector).injectServices(subFlow)
        verify(flowStackService).push(subFlow)

        // verify ordered calls
        inOrder(sandboxDependencyInjector, flowFiber, flowStackService, subFlow) {
            verify(sandboxDependencyInjector).injectServices(subFlow)
            verify(subFlow).call()

            // Assert the flow stack item is popped of the stack
            // and passed to  the sub flow finished IO request
            argumentCaptor<FlowIORequest.SubFlowFinished>().apply {
                verify(flowFiber).suspend(capture())

                assertThat(firstValue.flowStackItem).isEqualTo(flowStackItem)
            }
        }
    }

    @Test
    fun `sub flow completes with error`() {
        val flowEngine = FlowEngineImpl(flowFiberService)
        val error = Exception()

        whenever(subFlow.call()).doAnswer { throw error }

        val thrownError = assertThrows<Exception> { flowEngine.subFlow(subFlow) }

        assertThat(thrownError).isEqualTo(error)

        // verify unordered calls
        verify(sandboxDependencyInjector).injectServices(subFlow)
        verify(flowStackService).push(subFlow)

        // verify ordered calls
        inOrder(sandboxDependencyInjector, flowFiber, flowStackService, subFlow) {
            verify(sandboxDependencyInjector).injectServices(subFlow)
            verify(subFlow).call()

            // Assert the flow stack item and exception are passed
            // to the SubFlowFailed IO request
            argumentCaptor<FlowIORequest.SubFlowFailed>().apply {
                verify(flowFiber).suspend(capture())

                assertThat(firstValue.exception).isEqualTo(error)
                assertThat(firstValue.flowStackItem).isEqualTo(flowStackItem)
            }
        }
    }
}