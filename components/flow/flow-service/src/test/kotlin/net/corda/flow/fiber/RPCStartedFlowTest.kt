package net.corda.flow.fiber

import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RPCStartedFlowTest {

    private companion object {
        private const val REQUEST_BODY = "request body"
    }

    @Test
    fun `invoking an rpc started flow passes the arguments correctly`() {
        val rpcStartedFlow = RPCStartedFlow(TestFlow(), TestRPCRequestData())
        val output = rpcStartedFlow.invoke()
        assertEquals(REQUEST_BODY, output)
    }

    private class TestRPCRequestData : RPCRequestData {
        override fun getRequestBody(): String {
            return REQUEST_BODY
        }

        override fun <T> getRequestBodyAs(jsonMarshallingService: JsonMarshallingService, clazz: Class<T>): T {
            TODO("Not yet implemented")
        }
    }

    private class TestFlow : RPCStartableFlow {
        override fun call(requestBody: RPCRequestData): String {
            return requestBody.getRequestBody()
        }
    }
}