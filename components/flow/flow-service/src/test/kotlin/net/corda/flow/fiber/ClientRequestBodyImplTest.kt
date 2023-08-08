package net.corda.flow.fiber

import net.corda.data.flow.FlowStartContext
import net.corda.flow.TestMarshallingService
import net.corda.flow.state.FlowCheckpoint
import net.corda.test.util.identity.createTestHoldingIdentity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ClientRequestBodyImplTest {

    private companion object {
        private const val TEST_START_ARGS = "{\"foo\":\"bar\"}"
        private const val LIST_START_ARGS = "[{\"foo\":\"bar\"},{\"foo\":\"baz\"}]"
    }

    @Test
    fun `rpc request data is retrieved when getRequestBody is called`() {
        val requestData = ClientRequestBodyImpl(setupStartArgs(TEST_START_ARGS))
        assertEquals(TEST_START_ARGS, requestData.requestBody)
    }

    @Test
    fun `rpc request data can marshal request body to a type`() {
        val requestData = ClientRequestBodyImpl(setupStartArgs(TEST_START_ARGS))
        assertEquals(TestData("bar"), requestData.getRequestBodyAs(TestMarshallingService(), TestData::class.java))
    }

    @Test
    fun `rpc request data can marshal request body to a list of types`() {
        val requestData = ClientRequestBodyImpl(setupStartArgs(LIST_START_ARGS))
        assertEquals(
            listOf(TestData("bar"), TestData("baz")),
            requestData.getRequestBodyAsList(TestMarshallingService(), TestData::class.java)
        )
    }


    private data class TestData(val foo: String)

    private fun setupStartArgs(args: String): FlowFiberService {
        val fiberService = mock<FlowFiberService>()
        val fiber = mock<FlowFiber>()
        val checkpoint = mock<FlowCheckpoint>()
        val startContext = FlowStartContext().apply {
            startArgs = args
        }
        whenever(checkpoint.flowStartContext).thenReturn(startContext)
        val holdingIdentity = createTestHoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "12345")
        val executionContext = FlowFiberExecutionContext(
            checkpoint,
            mock(),
            holdingIdentity,
            mock(),
            mock(),
            emptyMap(),
            mock(),
            emptyMap()
        )
        whenever(fiber.getExecutionContext()).thenReturn(executionContext)
        whenever(fiberService.getExecutingFiber()).thenReturn(fiber)
        return fiberService
    }
}