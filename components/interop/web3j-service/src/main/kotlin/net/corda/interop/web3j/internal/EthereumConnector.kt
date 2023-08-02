package net.corda.interop.web3j.internal

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import com.google.gson.Gson
import kotlin.reflect.KClass

data class RpcRequest(
    val jsonrpc: String,
    val id: String,
    val method: String,
    val params: List<*>
)


data class RPCResponse (
    val success:Boolean,
    val message: String
)

data class Response (
    val id: String,
    val jsonrpc: String,
    val result: Any?
)

data class ContractDeploymentResponse (
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

    private val gson = Gson()
    private val maxLoopedRequests = 10


    /**
     * Finds the appropriate data class from the candidateDataClasses list that fits the JSON structure.
     *
     * @param json The JSON string to be parsed.
     * @param candidateDataClasses The list of candidate data classes to try parsing the JSON.
     * @return The matching data class from candidateDataClasses, or null if no match is found.
     */
    private fun findDataClassForJson(json: String, candidateDataClasses: List<KClass<*>>): KClass<*>? {
        for (dataClass in candidateDataClasses) {
            try {
                gson.fromJson(json, dataClass.java)
                return dataClass
            } catch (e: Exception) {
                println(e.message)
                // Parsing failed, continue with the next candidate data class
            }
        }
        return null // If no candidate data class fits the JSON structure
    }

    /**
     * Returns the useful data from the given input based on its type.
     *
     * @param input The input data object to process.
     * @return The useful data extracted from the input as a string, or an empty string if not applicable.
     */
    private fun returnUsefulData(input: Any): String {
        when (input) {
            is ContractDeploymentResponse -> return input.result.contractAddress
            is Response -> return input.result.toString()
        }
        return ""
    }



    /**
     * Makes an RPC call to the Ethereum node and returns the JSON response as an RPCResponse object.
     *
     * @param rpcUrl The URL of the Ethereum RPC endpoint.
     * @param method The RPC method to call.
     * @param params The parameters for the RPC call.
     * @return An RPCResponse object representing the result of the RPC call.
     */
    fun rpcCall(rpcUrl: String, method: String, params: List<Any?>): RPCResponse {
        try {
            val client = OkHttpClient()
            val body = RpcRequest("2.0", "90.0", method, params)
            val requestBase = gson.toJson(body)
            val requestBody = requestBase.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(rpcUrl)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            response.close()

            if (responseBody == null) {
                // Retry for network error
                return RPCResponse(false, "RPC Response was null")
            }

            return RPCResponse(true, responseBody)

        } catch (e: Exception) {
            e.printStackTrace()
            return RPCResponse(false, e.message.toString())
        }
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
    fun makeRequest(
        rpcUrl: String,
        method: String,
        params: List<*>,
        waitForResponse: Boolean,
        requests: Int
    ): Response {
        // Check if the maximum number of requests has been reached
        if (requests > maxLoopedRequests) {
            return Response("90", "2.0", "Timed Out")
        }

        // Make the RPC call to the Ethereum node
        val response = rpcCall(rpcUrl, method, params)
        val responseBody = response.message
        val success = response.success

        // Handle the response based on success status
        if (!success) {
            return Response("90", "2.0", "")
        }

        // Parse the JSON response into the base response object
        val baseResponse = gson.fromJson<Response>(responseBody, Response::class.java)

        // If the base response is null and waitForResponse is true, wait for 2 seconds and make a recursive call
        if (baseResponse.result == null && waitForResponse) {
            TimeUnit.SECONDS.sleep(2)
            return makeRequest(rpcUrl, method, params, waitForResponse, requests + 1) // Return the recursive call
        }

        // Find the appropriate data class for parsing the actual response
        val responseType = findDataClassForJson(
            responseBody,
            listOf(ContractDeploymentResponse::class, Response::class)
        )

        // Parse the actual response using the determined data class
        val actualParsedResponse = gson.fromJson<Any>(responseBody, responseType?.java ?: Any::class.java)

        // Get the useful response data from the parsed response
        val usefulResponse = returnUsefulData(actualParsedResponse)

        return Response("90", "2.0", usefulResponse)
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