package net.corda.web3j.dispatcher

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.EvmResponse
import net.corda.data.interop.evm.request.Call
import net.corda.web3j.EthereumConnector
import net.corda.web3j.GenericResponse
import net.corda.web3j.constants.CALL
import net.corda.web3j.constants.LATEST
import net.corda.web3j.decoder.TransactionDecoder
import net.corda.web3j.encoder.TransactionEncoder


/**
 * Dispatcher used to make call methods to a Generic EVM Node
 *
 * @param evmConnector The evmConnector class used to make rpc calls to the node
 */
class CallDispatcher(private val evmConnector: EthereumConnector) : EvmDispatcher {

    private val encoder = TransactionEncoder()

    private val decoder = TransactionDecoder()
    override fun dispatch(evmRequest: EvmRequest): EvmResponse {
        val request = evmRequest.payload as Call

        val data = encoder.encode(request.function, request.parameters)
        // EVM Expects both data & input
        val callData = JsonNodeFactory.instance.objectNode()
            .put("to", evmRequest.to)
            .put("data", data)
            .put("input", data)

        val resp = evmConnector.send<GenericResponse>(evmRequest.rpcUrl, CALL, listOf(callData, LATEST))
        return EvmResponse(decoder.decode(resp.result.toString(), evmRequest.returnType))
    }
}