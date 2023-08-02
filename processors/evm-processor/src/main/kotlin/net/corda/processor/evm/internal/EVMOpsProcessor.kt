package net.corda.processors.evm.internal

import com.google.gson.JsonObject
import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.EvmResponse
//import net.corda.interop.web3j.internal.EthereumConnector

import net.corda.messaging.api.processor.RPCResponderProcessor
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import org.web3j.crypto.Credentials
import org.web3j.service.TxSignServiceImpl
import org.web3j.crypto.RawTransaction
import org.web3j.utils.Numeric
import java.math.BigInteger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import java.sql.Time
import java.util.concurrent.TimeUnit

import kotlin.reflect.KClass

fun findDataClassForJson(json: String, candidateDataClasses: List<KClass<*>>): KClass<*>? {
    val gson = Gson()

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
sealed class ApiResponse<out T>

data class Success<out T>(val data: T) : ApiResponse<T>()
data class Error(val message: String) : ApiResponse<Nothing>()


//val apiResponse = fetchDataFromApi()
//when (apiResponse) {
//    is Success -> {
//        val data = apiResponse.data
//        // Handle successful response
//    }
//    is Error -> {
//        val errorMessage = apiResponse.message
//        // Handle error response
//    }
//}


data class RpcRequest(
    val jsonrpc: String,
    val id: String,
    val method: String,
    val params: List<*>
)



data class Response (
    val id: String,
    val jsonrpc: String,
    val result: Any?,
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

//    private fun handleResponse

    private fun returnUsefulData(input: Any): String {
        when(input){
            is ContractDeploymentResponse -> return input.result.contractAddress
            is Response -> return input.result.toString()
        }

        return ""
    }
    fun makeRequest(rpcUrl: String, method: String, params: List<*>, waitForResponse: Boolean): Response {
        try {
            val gson = Gson()
            val client = OkHttpClient()
            val body = RpcRequest(
                jsonrpc = "2.0",
                id = "90.0",
                method = method,
                params = params
            )
            val requestBase = gson.toJson(body)
            val requestBody = requestBase.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(rpcUrl)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            println("Response: ${responseBody.toString()}")
//            val parsedResponse = gson.fromJson(responseBody, Response::class.java)
            response.close()

            val baseResponse = gson.fromJson(responseBody.toString(),Response::class.java)


            println("BASE RESPONSE ${baseResponse}")
            if(waitForResponse){
                if(baseResponse.result==null){
                    // if they
                    TimeUnit.SECONDS.sleep(2)
                    makeRequest(rpcUrl,method,params,true)
                }
            }
            val responseType = findDataClassForJson(responseBody.toString(),listOf(ContractDeploymentResponse::class, Response::class))

            val actualParsedResponse = gson.fromJson(responseBody.toString(),responseType?.java)

            println("AT Actual Response ${actualParsedResponse}")
            val usefullResponse = returnUsefulData(actualParsedResponse)
            println("USEFULL RESPONSE ${usefullResponse.toString()}")
            return Response("90.0","2.0",usefullResponse)

        } catch (e: Exception) {
            e.printStackTrace()
            return Response("","","")
        }
    }

    fun send(rpcUrl: String, method: String, params: List<*>): Response{
        return makeRequest(rpcUrl, method, params, false)
    }

    fun send(rpcUrl: String, method: String, params: List<*>, waitForResponse: Boolean): Response{
        return makeRequest(rpcUrl, method, params, waitForResponse)
    }
}


// This is a processor that will send transaction & calls to the respective EVM Network and
// Process back the return message.
class EVMOpsProcessor() : RPCResponderProcessor<EvmRequest, EvmResponse> {
    val evmConnector = EthereumConnector()
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
    // Get the transaction receipt details
    private fun getTransactionReceipt(rpcConnection: String, receipt: String): String? {
        val resp = evmConnector.send(rpcConnection,"eth_getTransactionReceipt",listOf(receipt))
        return resp.result.toString()
    }

    // Get the Chain Id
    private fun getChainId(rpcConnection: String): Long {
        val resp = evmConnector.send(rpcConnection,"eth_chainId",listOf(""))
        println("CHAIN ID = ${Numeric.toBigInt(resp.result.toString())}")
        return Numeric.toBigInt(resp.result.toString()).toLong()
    }

    // Get the set Gas Price
    private fun getGasPrice(rpcConnection: String,): BigInteger {
        val resp = evmConnector.send(rpcConnection,"eth_gasPrice", listOf(""))
        return BigInteger.valueOf(Integer.decode(resp.result.toString()).toLong())
    }

    // Estimate the Gas price
    private fun estimateGas(rpcConnection: String, from: String, to: String, payload: String): BigInteger {
        val rootObject= JsonObject()
        rootObject.addProperty("to",to)
        rootObject.addProperty("data",payload)
        rootObject.addProperty("input",payload)
        rootObject.addProperty("from",from)
        val resp = evmConnector.send(rpcConnection,"eth_estimateGas", listOf(rootObject,"latest"))
        return BigInteger.valueOf(Integer.decode(resp.result.toString()).toLong())
    }

    // Fetches the amount of transactions an address has made
    private fun getTransactionCount(rpcUrl: String, address: String): BigInteger {
        val transactionCountResponse = evmConnector.send(
            rpcUrl,
            "eth_getTransactionCount",
            listOf(address, "latest")
        );
        return BigInteger.valueOf(Integer.decode(transactionCountResponse.result.toString()).toLong())
    }



    private fun queryCompletion(rpcConnection: String, transactionHash: String): String {
        try{
            val res =  evmConnector.send(rpcConnection,"eth_getTransactionReceipt",listOf(transactionHash))

            if(res.result.toString()=="null"){
                TimeUnit.SECONDS.sleep(2)
                queryCompletion(rpcConnection,transactionHash)
            }
            return res.result.toString()
        }catch(e: Throwable){
            println(e)
            TimeUnit.SECONDS.sleep(2)
            queryCompletion(rpcConnection,transactionHash)
        }
        return "true"
    }


    private fun queryCompletionContract(rpcConnection: String, transactionHash: String): String {
        val resp = evmConnector.send(rpcConnection,"eth_getTransactionReceipt",listOf(transactionHash),true)
        return resp.result.toString()
    }
    // Sends off a transaction
    private fun sendTransaction(rpcConnection: String, contractAddress: String, payload: String): String{
        val nonce = getTransactionCount(rpcConnection, "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73")
        val estimatedGas = estimateGas(rpcConnection,"0xfe3b557e8fb62b89f4916b721be55ceb828dbd73", contractAddress, payload)

        // Usefor for pre EIP-1559 ones
        println("Estimated Gas: ${estimatedGas}")

        val chainid = getChainId(rpcConnection)

        val transaction = RawTransaction.createTransaction(
            chainid,
            nonce,
            BigInteger.valueOf(10000000),
            contractAddress,
            BigInteger.valueOf(0),
            payload,
            BigInteger.valueOf(10000000),
            BigInteger.valueOf(51581475500)
        )

        println("Passed transaction")

        val signer = Credentials.create("0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63")
        println("Passed Credentials")

        val signed = TxSignServiceImpl(signer).sign(transaction,"1337".toLong())
        println("Passed Signing")
        println(Numeric.toHexString(signed))
        val tReceipt = evmConnector.send(rpcConnection, "eth_sendRawTransaction",listOf(Numeric.toHexString(signed))).result.toString()
        println("Receipt: ${tReceipt}")
        if(contractAddress.isEmpty()){
            val transactionOutput = queryCompletionContract(rpcConnection,tReceipt)
            println("Transaction Details: ${transactionOutput}")

        }else{
            val transactionOutput = queryCompletion(rpcConnection,tReceipt)
            println("Transaction Details: ${transactionOutput}")

        }
        return tReceipt
    }


    // Make a smart contract call
    private fun sendCall(rpcConnection: String, contractAddress: String, payload: String): String{
        val rootObject= JsonObject()
        rootObject.addProperty("to",contractAddress)
        rootObject.addProperty("data",payload)
        rootObject.addProperty("input",payload)
        val resp = evmConnector.send(rpcConnection,"eth_call", listOf(rootObject,"latest"))
        return resp.result.toString()
    }



    override fun onNext(request: EvmRequest, respFuture: CompletableFuture<EvmResponse>) {
        log.info(request.schema.toString(true))
        // Paramaters for the transaction/queryS
        val contractAddress = request.contractAddress
        val rpcConnection = request.rpcUrl
        val payload = request.payload
        val flowId = request.flowId
        val isTransaction = request.isTransaction

        try {
            if (isTransaction) {
                // Transaction Being Sent
                val transactionOutput = sendTransaction(rpcConnection, contractAddress, payload)

                val result = EvmResponse(flowId,transactionOutput)
                respFuture.complete(result)

            } else {
                // Call Being Sent
                val callResult =  sendCall(rpcConnection,contractAddress,payload)
                respFuture.complete(EvmResponse(flowId,callResult))
            }
        }catch (e: Throwable){
            // Better error handling => Meaningful
            println(e)
            println(e.message)
            respFuture.completeExceptionally(e)
        }
    }
}