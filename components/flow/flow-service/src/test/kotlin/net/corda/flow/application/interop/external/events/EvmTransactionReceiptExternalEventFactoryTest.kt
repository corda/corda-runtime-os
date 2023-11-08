package net.corda.flow.application.interop.external.events

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.request.GetTransactionReceipt
import net.corda.v5.application.interop.evm.TransactionReceipt
import net.corda.v5.application.interop.evm.options.EvmOptions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class EvmTransactionReceiptExternalEventFactoryTest {

    @Test
    fun `transaction receipt with correct parameter list sends external event`() {
        val context = ExternalEventContext("", "", KeyValuePairList())
        val expectedRequest = EvmRequest(
            "from",
            "",
            "rpcUrl",
            TransactionReceipt::class.java.name,
            GetTransactionReceipt("hash"),
            context
        )

        val eventExecutor = EvmTransactionReceiptExternalEventFactory()
        val options = EvmOptions(
            "rpcUrl",
            "from"
        )

        val result = eventExecutor.createExternalEvent(
            mock(),
            context,
            EvmTransactionReceiptExternalEventParams(
                options,
                "hash",
            )
        )
        assertThat(result.payload).isEqualTo(expectedRequest)
    }
}