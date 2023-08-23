package net.corda.web3j.dispatcher

import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.EvmResponse
import net.corda.data.interop.evm.request.GetTransactionReceipt
import net.corda.web3j.EthereumConnector
import net.corda.web3j.Response
import net.corda.web3j.constants.GET_TRANSACTION_RECEIPT

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