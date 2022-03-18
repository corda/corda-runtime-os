package net.corda.flow.application.services

import net.corda.data.flow.FlowStackItem
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.fiber.FlowStackService
import net.corda.flow.pipeline.sandbox.SandboxDependencyInjector
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

    private val flowFiberService = mock<FlowFiberService>()
    private val flowStackService = mock<FlowStackService>()
    private val sandboxDependencyInjector = mock<SandboxDependencyInjector>()
    private val flowFiberExecutionContext = FlowFiberExecutionContext(
        sandboxDependencyInjector,
        flowStackService,
        mock(),
        mock(),
        HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "group1")
    )

    private val flowStackItem = FlowStackItem()
    private val subFlow = mock<Flow<String>>()
    private val flowFiber = mock<FlowFiber<*>>()
    private val result = "result"

    @BeforeEach
    fun setup() {
        whenever(flowFiberService.getExecutingFiber()).thenReturn(flowFiber)
        whenever(flowFiber.getExecutionContext()).thenReturn(flowFiberExecutionContext)
        whenever(subFlow.call()).thenReturn(result)
        whenever(flowStackService.pop()).thenReturn(flowStackItem)
    }

    @Test
    fun `get virtual node name returns holders x500 name`(){
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

                assertThat(firstValue.result).isEqualTo(flowStackItem)
            }
        }
    }

    @Test
    fun `sub flow completes with error`() {
        val flowEngine = FlowEngineImpl(flowFiberService)
        val error = Exception()

        whenever(subFlow.call()).doAnswer{ throw error}

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
                assertThat(firstValue.result).isEqualTo(flowStackItem)
            }
        }
    }
}