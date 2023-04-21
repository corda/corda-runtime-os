package net.corda.simulator.runtime

import net.corda.simulator.runtime.serialization.SimpleJsonMarshallingService
import net.corda.simulator.runtime.testflows.HelloFlow
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test

class RPCRequestDataWrapperTest {

    @Test
    fun `should create a valid RPC request from individual values`() {
        val requestBody = RPCRequestDataWrapper(
            "r1",
            HelloFlow::class.java.name,
            "{ \"a\" : 6, \"b\" : 7 }"
        )
        MatcherAssert.assertThat(
            requestBody.toRPCRequestData().getRequestBodyAs(
                SimpleJsonMarshallingService(),
                RPCRequestDataWrapperFactoryTest.InputMessage::class.java
            ),
            Matchers.`is`(RPCRequestDataWrapperFactoryTest.InputMessage(6, 7))
        )
    }
}