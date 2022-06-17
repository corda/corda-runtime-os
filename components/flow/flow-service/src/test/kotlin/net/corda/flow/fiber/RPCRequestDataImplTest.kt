package net.corda.flow.fiber

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.data.flow.FlowStartContext
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.state.FlowCheckpoint
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.marshalling.MarshallingService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RPCRequestDataImplTest {

    private companion object {
        private const val TEST_START_ARGS = "{\"foo\":\"bar\"}"
    }

    @Test
    fun `rpc request data is retrieved when getRequestBody is called`() {
        val requestData = RPCRequestDataImpl(setupStartArgs())
        assertEquals(TEST_START_ARGS, requestData.getRequestBody())
    }

    @Test
    fun `rpc request data can marshal request body to a type`() {
        val requestData = RPCRequestDataImpl(setupStartArgs())
        assertEquals(TestData("bar"), requestData.getRequestBodyAs<TestData>(TestMarshallingService()))
    }


    private class TestMarshallingService : MarshallingService {
        val objectMapper = ObjectMapper().apply {
            registerModule(KotlinModule.Builder().build())
        }
        override fun format(data: Any): String {
            throw NotImplementedError()
        }

        override fun <T> parse(input: String, clazz: Class<T>): T {
            return objectMapper.readValue(input, clazz)
        }

        override fun <T> parseList(input: String, clazz: Class<T>): List<T> {
            throw NotImplementedError()
        }
    }

    private data class TestData(val foo: String)

    private fun setupStartArgs(): FlowFiberService {
        val fiberService = mock<FlowFiberService>()
        val fiber = mock<FlowFiber>()
        val checkpoint = mock<FlowCheckpoint>()
        val startContext = FlowStartContext().apply {
            startArgs = "{\"foo\":\"bar\"}"
        }
        whenever(checkpoint.flowStartContext).thenReturn(startContext)
        val holdingIdentity = HoldingIdentity().apply {
            x500Name = "CN=Alice, O=Alice Corp, L=LDN, C=GB"
            groupId = "12345"
        }
        val executionContext = FlowFiberExecutionContext(checkpoint, mock(), holdingIdentity, mock())
        whenever(fiber.getExecutionContext()).thenReturn(executionContext)
        whenever(fiberService.getExecutingFiber()).thenReturn(fiber)
        return fiberService
    }
}