package net.corda.interop.web3j.internal

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlin.reflect.KClass
import com.google.gson.JsonParser
import net.corda.v5.base.exceptions.CordaRuntimeException


data class JsonRpcResponse(
    val jsonrpc: String,
    val id: String,
    val result: String
)


class EVMErrorException(val errorResponse: JsonRpcError) : Exception("Custom error")

data class JsonRpcError(
    val jsonrpc: String,
    val id: String,
    val error: Error
)

data class Error(
    val code: Int,
    val message: String,
    val data: String
)

data class RpcRequest(
    val jsonrpc: String,
    val id: String,
    val method: String,
    val params: List<*>
)


data class ProcessedResponse(
    val success: Boolean,
    val payload: String
)

data class RPCResponse (
    val success:Boolean,
    val message: String
)



data class Response (
    val id: String,
    val jsonrpc: String,
    val result: Any?,
    val success: Boolean
)

data class TransactionResponse (
    val id: String,
    val jsonrpc: String,
    val result: TransactionData
)

data class TransactionData(
    val blockHash: String,
    val blockNumber: String,
    val contractAddress: String,
    val cumulativeGasUsed: String,
    val from: String,
    val gasUsed: String,
    val effectiveGasPrice: String,
    val logs: List<TransactionLog>,
    val logsBloom: String,
    val status: String,
    val to: String?,
    val transactionHash: String,
    val transactionIndex: String,
    val type: String
)

data class TransactionLog(
    val address: String,
    val topics: List<String>,
    val data: String,
    val blockNumber: String,
    val transactionHash: String,
    val transactionIndex: String,
    val blockHash: String,
    val logIndex: String,
    val removed: Boolean
)

class EthereumConnector {

    companion object {
        private const val JSON_RPC_VERSION = "2.0"
    }

    private val gson = Gson()
    private val maxLoopedRequests = 10

    private fun checkNestedKey(jsonObject: JsonObject, nestedKey: String): Boolean {
        if (jsonObject.has(nestedKey)) {
            return true
        }

        for ((_, value) in jsonObject.entrySet()) {
            if (value.isJsonObject) {
                if (checkNestedKey(value.asJsonObject, nestedKey)) {
                    return true
                }
            }
        }

        return false
    }

    private fun jsonStringContainsNestedKey(jsonString: String, nestedKey: String): Boolean {
        return try {
            val jsonObject = JsonParser().parse(jsonString).asJsonObject
            checkNestedKey(jsonObject, nestedKey)
        } catch (e: Exception) {
            // Handle any parsing errors here
            false
        }
    }


    private fun jsonStringContainsKey(jsonString: String, key: String): Boolean {
        return try {
            val jsonObject = JsonParser().parse(jsonString).asJsonObject
            jsonObject.has(key)
        } catch (e: Exception) {
            // Handle any parsing errors here
            false
        }
    }
    /**
     * Finds the appropriate data class from the candidateDataClasses list that fits the JSON structure.
     *
     * @param json The JSON string to be parsed.
     * @return The matching data class from candidateDataClasses, or null if no match is found.
     */
    private fun findDataClassForJson(json: String): KClass<*>? {
        if (jsonStringContainsKey(json, "error")) {
            return JsonRpcError::class
        } else if (jsonStringContainsNestedKey(json, "contractAddress")) {
            return TransactionResponse::class
        } else {
            return JsonRpcResponse::class
        }

    }

    /**
     * Returns the useful data from the given input based on its type.
     *
     * @param input The input data object to process.
     * @return The useful data extracted from the input as a string, or an empty string if not applicable.
     */
    private fun returnUsefulData(input: Any): ProcessedResponse {
        println("INPUT ${input}")
        when (input) {
            is JsonRpcError -> {
                 throw EVMErrorException(input)
            }
            is TransactionResponse -> {
                try{
                    return ProcessedResponse(true, input.result.contractAddress)
                }catch(e: Exception){
                    return ProcessedResponse(true, input.result.toString())
                }                }
            is JsonRpcResponse -> return ProcessedResponse(true,input.result.toString())
        }
        return ProcessedResponse(false,"")
    }



    /**
     * Makes an RPC call to the Ethereum node and returns the JSON response as an RPCResponse object.
     *
     * @param rpcUrl The URL of the Ethereum RPC endpoint.
     * @param method The RPC method to call.
     * @param params The parameters for the RPC call.
     * @return An RPCResponse object representing the result of the RPC call.
     */
    private fun rpcCall(rpcUrl: String, method: String, params: List<Any?>): RPCResponse {
        val body = RpcRequest(JSON_RPC_VERSION, "90.0", method, params)
        val requestBase = gson.toJson(body)
        val requestBody = requestBase.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(rpcUrl)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()
        return OkHttpClient().newCall(request).execute().body?.use {
            RPCResponse(true, it.string())
        } ?: throw CordaRuntimeException("Response was null")
    }
    /**
     * Makes an RPC request to the Ethereum node and waits for the response.
     *
     * @param rpcUrl The URL of the Ethereum RPC endpoint.
     * @param method The RPC method to call.
     * @param params The parameters for the RPC call.
     * @param waitForResponse Set to true if the function should wait for a response, otherwise false.
     * @param requests The number of requests made so far (used for recursive calls).
     * @return A Response object representing the result of the RPC call.
     */
    private fun makeRequest(
        rpcUrl: String,
        method: String,
        params: List<*>,
        waitForResponse: Boolean,
        requests: Int
    ): Response {
        // Check if the maximum number of requests has been reached
        if (requests > maxLoopedRequests) {
            return Response("90", "2.0", "Timed Out",false)
        }

        // Make the RPC call to the Ethereum node
        val response = rpcCall(rpcUrl, method, params)
        val responseBody = response.message
        val success = response.success

        // Handle the response based on success status
        if (!success) {
            println("Request Failed")
            return Response("90", "2.0", response.message,false)
        }

        // Parse the JSON response into the base response object
        println("Response Body: $responseBody ")
        val baseResponse = gson.fromJson<Response>(responseBody, Response::class.java)


        // If the base response is null and waitForResponse is true, wait for 2 seconds and make a recursive call
        // TODO: This is temporarily required for

        if (baseResponse.result == null && waitForResponse) {
            TimeUnit.SECONDS.sleep(2)
            return makeRequest(rpcUrl, method, params, waitForResponse, requests + 1) // Return the recursive call
        }

        // Find the appropriate data class for parsing the actual response
        val responseType = findDataClassForJson(
            responseBody
        )

        println("RESPONSE BODY: ${responseBody}")
        // Parse the actual response using the determined data class
        val actualParsedResponse = gson.fromJson<Any>(responseBody, responseType?.java ?: Any::class.java)
        // Get the useful response data from the parsed response
        val usefulResponse = returnUsefulData(actualParsedResponse)

        return Response("90", "2.0", usefulResponse.payload, usefulResponse.success)
    }

    /**
     * Sends an RPC request to the Ethereum node and returns the response without waiting for it.
     *
     * @param rpcUrl The URL of the Ethereum RPC endpoint.
     * @param method The RPC method to call.
     * @param params The parameters for the RPC call.
     * @return A Response object representing the result of the RPC call.
     */
    fun send(rpcUrl: String, method: String, params: List<*>): Response {
        return makeRequest(rpcUrl, method, params, waitForResponse = false, requests = 0)
    }

    /**
     * Sends an RPC request to the Ethereum node and returns the response.
     *
     * @param rpcUrl The URL of the Ethereum RPC endpoint.
     * @param method The RPC method to call.
     * @param params The parameters for the RPC call.
     * @param waitForResponse Set to true if the function should wait for a response, otherwise false.
     * @return A Response object representing the result of the RPC call.
     */
    fun send(rpcUrl: String, method: String, params: List<*>, waitForResponse: Boolean): Response {
        return makeRequest(rpcUrl, method, params, waitForResponse, requests = 0)
    }

}