package net.corda.simulator.runtime

import net.corda.simulator.runtime.serialization.SimpleJsonMarshallingService
import net.corda.simulator.runtime.testflows.HelloFlow
import net.corda.simulator.runtime.testflows.ValidStartingFlow
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test

class RPCRequestDataWrapperFactoryTest {

    @Test
    fun `should create a valid RPC request from a string`() {
        val input = """
        {
          "httpStartFlow": {
            "clientRequestId": "r1",
            "flowClassName": "${HelloFlow::class.java.name}",
            "requestBody":  "{ \"a\" : 6, \"b\" : 7 }"
          }
        }
        """.trimIndent()
        val requestBody = RPCRequestDataWrapperFactory().create(input)
        assertThat(requestBody.toRPCRequestData().getRequestBody(), `is`("{ \"a\" : 6, \"b\" : 7 }"))
    }

    @Test
    fun `should create a valid RPC request from a input object`() {
        val requestData = RPCRequestDataWrapperFactory()
            .create("r1", ValidStartingFlow::class.java, InputMessage(6, 7))
        assertThat(
            requestData.toRPCRequestData().getRequestBodyAs(
                SimpleJsonMarshallingService(),
                InputMessage::class.java),
            `is`(InputMessage(6, 7)))
    }

    data class InputMessage(val a : Int, val b: Int)
}