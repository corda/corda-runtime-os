package net.corda.interop.evm

import com.fasterxml.jackson.databind.DatabindException
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.v5.base.exceptions.CordaRuntimeException

class EvmRpcError(message: String, private val code: Int) : CordaRuntimeException("Encountered an error ($code) when sending an RPC command to the Evm: $message")


/**
 * EthereumConnector Class
 *
 * This class facilitates interaction with an Ethereum node's RPC interface. It enables making RPC requests,
 * handling responses, and extracting useful data from those responses.
 *
 * @property evmRpc The reference to the EvmRPCCall service for making RPC calls to the Ethereum node.
 */
class EthereumConnector(
    private val evmRpc: EvmRPCCall,
) {
    private val objectMapper = ObjectMapper()

    /**
     * Makes an RPC request to the Ethereum node and waits for the response.
     *
     * @param rpcUrl The URL of the Ethereum RPC endpoint.
     * @param method The RPC method to call.
     * @param params The parameters for the RPC call.
     * @return A Response object representing the result of the RPC call.
     */
    fun <T> send(
        rpcUrl: String,
        method: String,
        params: List<*>,
        clazz: Class<T>,
    ): T {
        val response = evmRpc.rpcCall(rpcUrl, method, params)
        return try {
            objectMapper.readValue(response, clazz)
        } catch (e: DatabindException) {
            val error = objectMapper.readValue(response, JsonRpcError::class.java)
            throw EvmRpcError("Error on RPC Call: $error", error.error.code)
        }
    }

    inline fun <reified T> send(
        rpcUrl: String,
        method: String,
        params: List<*>,
    ) = send(rpcUrl, method, params, T::class.java)
}