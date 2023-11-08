package net.corda.flow.application.interop.external.events

import java.math.BigInteger
import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.request.Parameter
import net.corda.data.interop.evm.request.Transaction
import net.corda.flow.TestMarshallingService
import net.corda.v5.application.interop.evm.Type
import net.corda.v5.application.interop.evm.options.TransactionOptions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class EvmTransactionExternalEventFactoryTest {

    @Test
    fun `transaction with correct parameter list sends external event`() {
        val context = ExternalEventContext("", "", KeyValuePairList())
        val expectedRequest = EvmRequest(
            "from",
            "to",
            "rpcUrl",
            String::class.java.name,
            Transaction(
                "test",
                net.corda.data.interop.evm.request.TransactionOptions(
                    "1",
                    "1",
                    "1",
                    "1"),
                listOf(
                    Parameter("one", "int64", "1"),
                    Parameter("two", "address_list", """["one","two"]"""),
                    Parameter("three", "boolean_list", """[false,true]"""),
                    Parameter("four", "int32_array", """[1,2]"""),
                )
            ),
            context
        )

        val eventExecutor = EvmTransactionExternalEventFactory(TestMarshallingService())
        val options = TransactionOptions(
            BigInteger.ONE,
            BigInteger.ONE,
            BigInteger.ONE,
            BigInteger.ONE,
            "rpcUrl",
            "from"
        )

        val result = eventExecutor.createExternalEvent(
            mock(),
            context,
            EvmTransactionExternalEventParams(
                options,
                "test",
                "to",
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