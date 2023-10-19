package net.corda.flow.application.interop.external.events

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.request.Call
import net.corda.data.interop.evm.request.CallOptions
import net.corda.data.interop.evm.request.Parameter
import net.corda.flow.TestMarshallingService
import net.corda.v5.application.interop.evm.Type
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class EvmCallExternalEventFactoryTest {

    @Test
    fun `call with correct parameter list sends external event`() {
        val context = ExternalEventContext("", "", KeyValuePairList())
        val expectedRequest = EvmRequest(
            "from",
            "to",
            "rpcUrl",
            String::class.java.name,
            Call(
                "test", CallOptions("latest"),
                listOf(
                    Parameter("one", "int64", "1"),
                    Parameter("two", "address_list", """["one","two"]"""),
                    Parameter("three", "boolean_list", """[false,true]"""),
                    Parameter("four", "int32_array", """[1,2]"""),
                )
            ),
            context
        )

        val eventExecutor = EvmCallExternalEventFactory(TestMarshallingService())

        val result = eventExecutor.createExternalEvent(
            mock(),
            context,
            EvmCallExternalEventParams(
                net.corda.v5.application.interop.evm.options.CallOptions("latest", "rpcUrl", "from"),
                "test",
                "to",
                String::class.java,
                listOf(
                    net.corda.v5.application.interop.evm.Parameter.of("one", Type.INT64, 1),
                    net.corda.v5.application.interop.evm.Parameter("two", Type.ADDRESS_LIST, listOf("one", "two")),
                    net.corda.v5.application.interop.evm.Parameter("three", Type.BOOLEAN_LIST, listOf(false, true)),
                    net.corda.v5.application.interop.evm.Parameter("four", Type.INT32_ARRAY, arrayOf(1, 2)),
                )
            )
        )
        assertThat(result.payload).isEqualTo(expectedRequest)
    }
}