package net.corda.testutils.tools

import net.corda.testutils.flows.HelloFlow
import net.corda.testutils.flows.ValidStartingFlow
import net.corda.testutils.services.SimpleJsonMarshallingService
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test

class RPCRequestDataMockTest {

    private val record = InputMessage(6, 7)

    @Test
    fun `should create a valid RPC request from a string`() {
        val input = """
        {
          "httpStartFlow": {
            "clientRequestId": "r1",
            "flowClassName": "${HelloFlow::class.java.name}",
            "requestData":  "{ \"a\" : 6, \"b\" : 7 }"
          }
        }
        """.trimIndent()
        val requestBody = RPCRequestDataMock.fromJSonString(input)
        assertThat(requestBody.toRPCRequestData().getRequestBody(), `is`("{ \"a\" : 6, \"b\" : 7 }"))
    }

    @Test
    fun `should create a valid RPC request from individual values`() {
        val requestBody = RPCRequestDataMock("r1",
            "${HelloFlow::class.java.name}",
            "{ \"a\" : 6, \"b\" : 7 }")
        assertThat(
            requestBody.toRPCRequestData().getRequestBodyAs(
                SimpleJsonMarshallingService(),
            InputMessage::class.java),
            `is`(InputMessage(6, 7)))
    }

    @Test
    fun `should create a valid RPC request from a input object`() {
        val requestBody = RPCRequestDataMock.fromData("r1", ValidStartingFlow::class.java, InputMessage(6, 7))
        assertThat(
            requestBody.toRPCRequestData().getRequestBodyAs(
                SimpleJsonMarshallingService(),
                InputMessage::class.java),
            `is`(InputMessage(6, 7)))
    }

    data class InputMessage(val a : Int, val b: Int)
}