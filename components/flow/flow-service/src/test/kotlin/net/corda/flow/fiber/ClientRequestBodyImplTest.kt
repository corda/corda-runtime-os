package net.corda.flow.fiber

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.data.flow.FlowStartContext
import net.corda.flow.state.FlowCheckpoint
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.v5.application.marshalling.MarshallingService
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
            return objectMapper.readValue(
                input,
                objectMapper.typeFactory.constructCollectionType(List::class.java, clazz)
            )
        }

        override fun <K, V> parseMap(input: String, keyClass: Class<K>, valueClass: Class<V>): Map<K, V> {
            return objectMapper.readValue(
                input,
                objectMapper.typeFactory.constructMapType(LinkedHashMap::class.java, keyClass, valueClass)
            )
        }
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
        val executionContext = FlowFiberExecutionContext(checkpoint, mock(), holdingIdentity, mock(), mock(), emptyMap())
        whenever(fiber.getExecutionContext()).thenReturn(executionContext)
        whenever(fiberService.getExecutingFiber()).thenReturn(fiber)
        return fiberService
    }
}