package net.corda.interop.evm.dispatcher

import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.EvmResponse
import net.corda.data.interop.evm.request.GetTransactionReceipt
import net.corda.interop.evm.EthereumConnector
import net.corda.interop.evm.Response
import net.corda.interop.evm.constants.GET_TRANSACTION_RECEIPT

/**
 * Dispatcher used to get transaction receipt.
 *
 * @param evmConnector The evmConnector class used to make rpc calls to the node
 */
class GetTransactionReceiptDispatcher(private val evmConnector: EthereumConnector) : EvmDispatcher {

    override fun dispatch(evmRequest: EvmRequest): EvmResponse {
        val transactionReceipt = evmRequest.payload as GetTransactionReceipt

        val resp = evmConnector.send<Response>(
            evmRequest.rpcUrl,
            GET_TRANSACTION_RECEIPT,
            listOf(transactionReceipt.transactionHash)
        )
        return EvmResponse(resp.result.toString())
    }
}