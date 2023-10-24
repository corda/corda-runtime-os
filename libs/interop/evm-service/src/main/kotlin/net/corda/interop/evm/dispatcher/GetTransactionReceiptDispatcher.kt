package net.corda.interop.evm.dispatcher

import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.EvmResponse
import net.corda.data.interop.evm.request.GetTransactionReceipt
import net.corda.data.interop.evm.response.TransactionReceipt
import net.corda.interop.evm.EthereumConnector
import net.corda.interop.evm.GenericResponse
import net.corda.interop.evm.Response
import net.corda.interop.evm.constants.GET_TRANSACTION_RECEIPT

/**
 * Dispatcher used to get transaction receipt.
 *
 * @param evmConnector The evmConnector class used to make rpc calls to the node
 */
class GetTransactionReceiptDispatcher(private val evmConnector: EthereumConnector) : EvmDispatcher {

    override fun dispatch(evmRequest: EvmRequest): EvmResponse {
        val getTransactionReceipt = evmRequest.payload as GetTransactionReceipt

        val resp = evmConnector.send<GenericResponse>(
            evmRequest.rpcUrl,
            GET_TRANSACTION_RECEIPT,
            listOf(getTransactionReceipt.transactionHash)
        )

//        val transactionReceipt = TransactionReceipt.newBuilder()
//            .setTransactionHash(result.transactionHash)
//            .setTransactionIndex(result.transactionIndex)
//            .setBlockNumber(result.blockNumber.replace("0x",""))
//            .setBlockHash(result.blockHash)
//            .setContractAddress(result.contractAddress)
//            .setCumulativeGasUseåd(result.cumulativeGasUsed)
//            .setEffectiveGasPrice(result.effectiveGasPrice)
//            .setFrom(result.from).setGasUsed(result.gasUsed)
//            .setLogsBloom(result.logsBloom)
//            .setStatus(Integer.parseInt(result.status.replace("0x",""),16)!=0)
//            .setTo(result.to).setType(result.type)
//            .setLogs(emptyList())
//            .setGasUsed(result.gasUsed)
//            .setType(result.type)
//            .build()
//
        return EvmResponse(resp.result)
    }
}