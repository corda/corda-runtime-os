package net.corda.processors.evm.internal

import com.google.gson.JsonObject
import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.request.SendRawTransaction
import net.corda.data.interop.evm.EvmResponse
import net.corda.messaging.api.processor.RPCResponderProcessor
import org.slf4j.LoggerFactory
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.service.TxSignServiceImpl
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.util.concurrent.CompletableFuture
import net.corda.interop.web3j.internal.EthereumConnector
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import net.corda.data.interop.evm.request.Call
import net.corda.data.interop.evm.request.ChainId
import net.corda.data.interop.evm.request.EstimateGas
import net.corda.data.interop.evm.request.GasPrice
import net.corda.data.interop.evm.request.GetBalance
import net.corda.data.interop.evm.request.GetCode
import net.corda.data.interop.evm.request.GetTransactionByHash
import net.corda.data.interop.evm.request.GetTransactionReceipt
import net.corda.data.interop.evm.request.MaxPriorityFeePerGas
import net.corda.data.interop.evm.request.Syncing
import net.corda.interop.web3j.internal.EVMErrorException
import java.util.concurrent.TimeUnit

class TransientErrorException(message: String) : Exception(message)

// GOING TO GET RID OF gson

/**
 * EVMOpsProcessor is an implementation of the RPCResponderProcessor for handling Ethereum Virtual Machine (EVM) requests.
 * It allows executing smart contract calls and sending transactions on an Ethereum network.
 */
class EVMOpsProcessor : RPCResponderProcessor<EvmRequest, EvmResponse> {

    private val MAX_RETRIES = 3
    private val RETRY_DELAY_MS = 1000L // 1 second
    private val evmConnector = EthereumConnector()
    private val scheduledExecutorService = Executors.newFixedThreadPool(20)
    private val transientEthereumErrorCodes = listOf(
        -32000, -32005, -32010, -32016, -32002,
        -32003, -32004, -32007, -32008, -32009,
        -32011, -32012, -32014, -32015, -32019,
        -32020, -32021
    )
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }


    inner class RetryPolicy(private val maxRetries: Int, private val delayMs: Long) {
        fun execute(action: () -> Unit): Unit {
            var retries = 0
            while (retries <= maxRetries) {
                try {
                    return action()
                } catch (e: EVMErrorException) {
                    if(transientEthereumErrorCodes.contains(e.errorResponse.error.code)){
                        // Log or handle the error if needed
                        retries++
                        if (retries <= maxRetries) {
                            TimeUnit.MILLISECONDS.sleep(delayMs)
                        } else {
                            throw e // If retries exhausted, rethrow the exception
                        }
                    }else{
                        throw e
                    }
                }
            }
            throw IllegalStateException("Execution should not reach here")
        }
    }


    /**
     * Retrieves the balance of a given Ethereum address using the provided RPC connection.
     *
     * @param rpcConnection The RPC connection URL for Ethereum communication.
     * @param from The Ethereum address for which to retrieve the balance.
     * @return The balance of the specified Ethereum address as a string.
     */
    fun getBalance(rpcConnection: String, from: String): String {
        // Send an RPC request to retrieve the balance of the specified address.
        val resp = evmConnector.send(rpcConnection, "eth_getBalance", listOf(from, "latest"))

        // Return the balance as a string.
        return resp.result.toString()
    }



    /**
     * Get the transaction receipt details from the Ethereum node.
     *
     * @param rpcConnection The URL of the Ethereum RPC endpoint.
     * @param receipt The receipt of the transaction.
     * @return The JSON representation of the transaction receipt, or null if not found.
     */
    fun getTransactionReceipt(rpcConnection: String, receipt: String): CompletableFuture<String> {
        return CompletableFuture.supplyAsync<String>{
            val resp = evmConnector.send(rpcConnection, "eth_getTransactionReceipt", listOf(receipt))
            resp.result.toString()
        }
    }


    /**
     * Retrieves transaction details for a given Ethereum transaction hash using the provided RPC connection.
     *
     * @param rpcConnection The RPC connection URL for Ethereum communication.
     * @param hash The transaction hash for which to retrieve the details.
     * @return The transaction details as a string.
     */
    fun getTransactionByHash(rpcConnection: String, hash: String): String {
        // Send an RPC request to retrieve transaction details for the specified hash.
        val resp = evmConnector.send(rpcConnection, "eth_getTransactionByHash", listOf(hash))

        // Return the transaction details as a string.
        return resp.result.toString()
    }



    /**
     * Get the Chain ID from the Ethereum node.
     *
     * @param rpcConnection The URL of the Ethereum RPC endpoint.
     * @return The chain Id as a Long.
     */
    private fun getChainId(rpcConnection: String): Long {
        val resp = evmConnector.send(rpcConnection, "eth_chainId", listOf(""))
        println("CHAIN ID = ${Numeric.toBigInt(resp.result.toString())}")
        return Numeric.toBigInt(resp.result.toString()).toLong()
    }

    /**
     * Get the set Gas Price from the Ethereum node.
     *
     * @param rpcConnection The URL of the Ethereum RPC endpoint.
     * @return The Gas Price as a BigInteger.
     */
    private fun getGasPrice(rpcConnection: String): BigInteger {
        val resp = evmConnector.send(rpcConnection, "eth_gasPrice", listOf(""))
        return BigInteger.valueOf(Integer.decode(resp.result.toString()).toLong())
    }



    /**
     * Retrieves the maximum priority fee per gas using the provided RPC connection.
     *
     * @param rpcConnection The RPC connection URL for Ethereum communication.
     * @return The maximum priority fee per gas as a BigInteger.
     */
    private fun maxPriorityFeePerGas(rpcConnection: String): BigInteger {
        // Send an RPC request to retrieve the maximum priority fee per gas.
        val resp = evmConnector.send(rpcConnection, "eth_maxPriorityFeePerGas", listOf(""))

        // Return the maximum priority fee per gas as a BigInteger.
        return BigInteger.valueOf(Integer.decode(resp.result.toString()).toLong())
    }

    /**
     * Retrieves the code at a specific Ethereum address using the provided RPC connection.
     *
     * @param rpcConnection The RPC connection URL for Ethereum communication.
     * @param address The Ethereum address for which to retrieve the code.
     * @param blockNumber The block number at which to retrieve the code.
     * @return The code at the specified Ethereum address and block number as a string.
     */
    private fun getCode(rpcConnection: String, address: String, blockNumber: String): String {
        // Send an RPC request to retrieve the code at the specified address and block number.
        val resp = evmConnector.send(rpcConnection, "eth_getCode", listOf(address, blockNumber))

        // Return the code as a string.
        return resp.result.toString()
    }



    /**
     * Estimate the Gas price for a transaction.
     *
     * @param rpcConnection The URL of the Ethereum RPC endpoint.
     * @param from The sender's address.
     * @param to The recipient's address.
     * @param payload The payload data for the transaction.
     * @return The estimated Gas price as a BigInteger.
     */
    private fun estimateGas(rpcConnection: String, from: String, to: String, payload: String): BigInteger {
        val rootObject = JsonObject()
        rootObject.addProperty("to", to)
        rootObject.addProperty("data", payload)
        rootObject.addProperty("input", payload)
        rootObject.addProperty("from", from)
        val resp = evmConnector.send(rpcConnection, "eth_estimateGas", listOf(rootObject, "latest"))
        println("GAS RESP: $resp")
        return BigInteger.valueOf(Integer.decode(resp.result.toString()).toLong())
    }

    /**
     * Fetch the number of transactions made by an address.
     *
     * @param rpcUrl The URL of the Ethereum RPC endpoint.
     * @param address The address for which to fetch the transaction count.
     * @return The transaction count as a BigInteger.
     */
    private fun getTransactionCount(rpcUrl: String, address: String): BigInteger {
        val transactionCountResponse = evmConnector.send(
            rpcUrl,
            "eth_getTransactionCount",
            listOf(address, "latest")
        )
        return BigInteger.valueOf(Integer.decode(transactionCountResponse.result.toString()).toLong())
    }

    /**
     * Query the completion status of a contract using the Ethereum node.
     *
     * @param rpcConnection The URL of the Ethereum RPC endpoint.
     * @param transactionHash The hash of the transaction to query.
     * @return The JSON representation of the transaction receipt.
     */
    private fun queryCompletionContract(rpcConnection: String, transactionHash: String): String {
        val resp = evmConnector.send(rpcConnection, "eth_getTransactionReceipt", listOf(transactionHash), true)
        return resp.result.toString()
    }

    private suspend fun prepareTransaction(rpcConnection: String): Pair<BigInteger, Long> = coroutineScope {
        val nonceDeferred = async { getTransactionCount(rpcConnection, "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73") }
        val chainIdDeferred = async { getChainId(rpcConnection) }

        val nonce = nonceDeferred.await()
        val chainId = chainIdDeferred.await()

        nonce to chainId
    }

    /**
     * Send a transaction to the Ethereum network.
     *
     * @param rpcConnection The URL of the Ethereum RPC endpoint.
     * @param contractAddress The address of the smart contract.
     * @param payload The payload data for the transaction.
     * @return The receipt of the transaction.
     */
    private suspend fun sendTransaction(rpcConnection: String, contractAddress: String, payload: String): String {
        // Do this async
        val (nonce, chainId) = prepareTransaction(rpcConnection)

        val transaction = RawTransaction.createTransaction(
                chainId,
                nonce,
                BigInteger.valueOf(10000000),
                contractAddress,
                BigInteger.valueOf(0),
                payload,
                BigInteger.valueOf(10000000),
                BigInteger.valueOf(51581475500)
            )

            val signer = Credentials.create("0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63")
            println("Passed Credentials")

            val signed = TxSignServiceImpl(signer).sign(transaction, "1337".toLong())
            println("Passed Signing")
            println(Numeric.toHexString(signed))
            val tReceipt =
                evmConnector.send(rpcConnection, "eth_sendRawTransaction", listOf(Numeric.toHexString(signed)))
            println("Receipt: $tReceipt")
            if (!tReceipt.success) {
                return tReceipt.result.toString()
            }

            // Exception Case When Contract is Being Created we need to wait the address
        println("CONTRACT ADDRESS EMPTY: $contractAddress")
        return if (contractAddress.isEmpty()) {
            queryCompletionContract(rpcConnection, tReceipt.result.toString())
        } else {
            tReceipt.result.toString()
        }
    }

    /**
     * Make a smart contract call to the Ethereum network.
     *
     * @param rpcConnection The URL of the Ethereum RPC endpoint.
     * @param contractAddress The address of the smart contract.
     * @param payload The payload data for the contract call.
     * @return The result of the contract call.
     */
    private fun sendCall(rpcConnection: String, contractAddress: String, payload: String): String {
        val rootObject = JsonObject()
        rootObject.addProperty("to", contractAddress)
        rootObject.addProperty("data", payload)
        rootObject.addProperty("input", payload)
        val resp = evmConnector.send(rpcConnection, "eth_call", listOf(rootObject, "latest"))
        return resp.result.toString()
    }

    // TODO: Add more loggin
    // Talk to owen about tracing work

    // Joining in a thread pool is an option
    // Transient Error gets added back on the queue


    private fun handleRequest(request: EvmRequest, respFuture: CompletableFuture<EvmResponse>) {
        log.info(request.schema.toString(true))
        // Parameters for the transaction/query
        val rpcConnection = request.rpcUrl
        val data = request.specificData.toString()
        val flowId = request.flowId
        val from = request.from
        val to = request.to
        val payload = request.payload



        when (payload) {
            is Call -> {
                // Handle the Call
                println("PAYLOAD: ${payload.payload}")
                val callResult = sendCall(rpcConnection, to, payload.payload)
                respFuture.complete(EvmResponse(flowId, callResult))
             }
            is ChainId -> {
                // Handle the Chain Id
                val chainId = getChainId(rpcConnection)
                respFuture.complete(EvmResponse(flowId,chainId.toString()))
            }
            is EstimateGas -> {
                val estimateGas = estimateGas(rpcConnection,from,to,payload.payload.toString())
                respFuture.complete(EvmResponse(flowId,estimateGas.toString()))
            }
            is GasPrice -> {
                val gasPrice = getGasPrice(rpcConnection)
                respFuture.complete(EvmResponse(flowId,gasPrice.toString()))
            }
            is GetBalance -> {
                val balance = getBalance(rpcConnection,from)
                respFuture.complete(EvmResponse(flowId,balance.toString()))
            }
            is GetCode -> {
                val code = getCode(rpcConnection,payload.address,payload.blockNumber)
                respFuture.complete(EvmResponse(flowId, code))
            }
            is GetTransactionByHash -> {
                val transaction = getTransactionByHash(rpcConnection,data)
                respFuture.complete(EvmResponse(flowId, transaction.toString()))

            }
            is GetTransactionReceipt -> {
                val receipt = getTransactionReceipt(rpcConnection,data)
                respFuture.complete(EvmResponse(flowId,receipt.toString()))
            }
            is MaxPriorityFeePerGas -> {
                val maxPriority = maxPriorityFeePerGas(rpcConnection)
                respFuture.complete(EvmResponse(flowId,maxPriority.toString()))
            }
            is SendRawTransaction -> {
                var transactionOutput: String;
                runBlocking {
                    transactionOutput = sendTransaction(rpcConnection, to, payload.payload)
                }
                val result = EvmResponse(flowId, transactionOutput)
                respFuture.complete(result)
            }
            is Syncing -> {

            }

        }

//
//        if (isTransaction) {
//            // Transaction Being Sentx
//            var transactionOutput: String;
//            runBlocking {
//                transactionOutput = sendTransaction(rpcConnection, contractAddress, payload)
//            }
//            val result = EvmResponse(flowId, transactionOutput)
//            respFuture.complete(result)
//        } else {
//            // Call Being Sent
//            val callResult = sendCall(rpcConnection, contractAddress, payload)
//            respFuture.complete(EvmResponse(flowId, callResult))
//        }
    }


    override fun onNext(request: EvmRequest, respFuture: CompletableFuture<EvmResponse>) {
        val retryPolicy = RetryPolicy(MAX_RETRIES, RETRY_DELAY_MS)

        scheduledExecutorService.submit {
            try {
                retryPolicy.execute {
                    handleRequest(request,respFuture)
                }
            } catch (e: Exception) {
                respFuture.completeExceptionally(e)
            }
        }

    }


}
