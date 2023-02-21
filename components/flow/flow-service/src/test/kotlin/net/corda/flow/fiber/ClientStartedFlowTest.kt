package net.corda.flow.fiber

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.marshalling.MarshallingService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClientStartedFlowTest {

    private companion object {
        private const val REQUEST_BODY = "request body"
    }

    @Test
    fun `invoking an rpc started flow passes the arguments correctly`() {
        val clientStartedFlow = ClientStartedFlow(TestFlow(), TestRPCRequestData())
        val output = clientStartedFlow.invoke()
        assertEquals(REQUEST_BODY, output)
    }

    private class TestRPCRequestData : ClientRequestBody {
        override fun getRequestBody(): String {
            return REQUEST_BODY
        }

        override fun <T : Any> getRequestBodyAs(marshallingService: MarshallingService, clazz: Class<T>): T {
            TODO("Not yet implemented")
        }

        override fun <T> getRequestBodyAsList(marshallingService: MarshallingService, clazz: Class<T>): List<T> {
            TODO("Not yet implemented")
        }
    }

    private class TestFlow : ClientStartableFlow {
        override fun call(requestBody: ClientRequestBody): String {
            return requestBody.requestBody
        }
    }
}