package net.corda.processor.evm.internal;

import java.util.concurrent.CompletableFuture
import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.EvmResponse
import net.corda.data.interop.evm.request.Call
import net.corda.interop.web3j.internal.EthereumConnector;
import net.corda.interop.web3j.internal.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test;
import org.mockito.kotlin.any
import org.mockito.kotlin.mock

class EVMOpsProcessorTest {

    companion object {
        private const val FLOW_ID = "RandomFlowId"
    }
    @Test
    fun `test a successful Call on a smart contract`() {
        val response = Response(
            FLOW_ID,
            "You have no money!",
            0L,
            true
        )
        val connector = mock<EthereumConnector> {
            on { this.send(any(), any(), any()) }.thenReturn(response)
        }
        val processor = EVMOpsProcessor(connector)

        // Dummy request
        val evmRequest = EvmRequest(
            FLOW_ID,
            "from",
            "to",
            "http://127.0.0.1:8545",
            Call("A balance query call")
        )

        val evmResponse = CompletableFuture<EvmResponse>()
        processor.onNext(evmRequest, evmResponse)
        val returnedResponse = evmResponse.get()
        // Ideally we should decode the payload here into a more easily readable response.
        val expected = EvmResponse(FLOW_ID, "0")
        assertThat(returnedResponse).isEqualByComparingTo(expected)
    }

}