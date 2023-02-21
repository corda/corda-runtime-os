package net.corda.flow.application.services

import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.flow.ALICE_X500_NAME
import net.corda.flow.utils.mutableKeyValuePairList
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.whenever

class InteropServiceImplTest {
    private companion object {
        const val FLOW_NAME = "flow name"
    }

    private val mockFlowFiberService = MockFlowFiberService()
    private val flowStackService = mockFlowFiberService.flowStack

    private val interopService = InteropServiceImpl(mockFlowFiberService)

    @Test
    fun `invokeInterop call returns the message 'This does not work yet!'`() {
        whenever(flowStackService.peek()).thenReturn(
            FlowStackItem(
                InteropServiceImplTest.FLOW_NAME,
                true,
                emptyList(),
                mutableKeyValuePairList(),
                mutableKeyValuePairList()
            )
        )
        assertDoesNotThrow { interopService.callFacade(ALICE_X500_NAME,"No facade name", "No facade method name") }
    }
}
