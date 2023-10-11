package net.corda.interop.evm

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.interop.evm.constants.DEFAULT_RPC_ID
import net.corda.interop.evm.constants.JSON_RPC_VERSION
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * EvmRPCCall Class
 *
 * This class facilitates making RPC calls to an Ethereum node using the JSON-RPC protocol.
 *
 * @property httpClient The reference to the OkHttpClient service for making HTTP requests.
 */
class EvmRPCCall(
    private val httpClient: OkHttpClient
) {

    companion object {
        private val objectMapper = ObjectMapper()
    }


    /**
     * Makes an RPC call to the Ethereum node and returns the JSON response as an RPCResponse object.
     *
     * @param rpcUrl The URL of the Ethereum RPC endpoint.
     * @param method The RPC method to call.
     * @param params The parameters for the RPC call.
     * @return An RPCResponse object representing the result of the RPC call.
     */
     fun rpcCall(rpcUrl: String, method: String, params: List<Any?>): String {
        val body = RpcRequest(JSON_RPC_VERSION, DEFAULT_RPC_ID, method, params)
        val requestBase = objectMapper.writeValueAsString(body)
        val requestBody = requestBase.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(rpcUrl)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        val response = httpClient.newCall(request).execute()
        if (response.isSuccessful) {
            return response.body.use {
                if(it==null){
                    throw CordaRuntimeException("Unsuccessfull response from JSONRpc call, Response is null: ${response.message}")
                }
                it.string()
            }
        } else {
            throw CordaRuntimeException("Unsuccessful response from JSONRpc call: ${response.message}")
        }
    }
}
