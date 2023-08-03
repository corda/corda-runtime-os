package net.corda.processors.evm.internal

import com.google.gson.JsonObject
import net.corda.data.interop.evm.EvmRequest
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

// GOING TO GET RID OF gson

/**
 * EVMOpsProcessor is an implementation of the RPCResponderProcessor for handling Ethereum Virtual Machine (EVM) requests.
 * It allows executing smart contract calls and sending transactions on an Ethereum network.
 */
class EVMOpsProcessor : RPCResponderProcessor<EvmRequest, EvmResponse> {

    private val evmConnector = EthereumConnector()
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    /**
     * Get the transaction receipt details from the Ethereum node.
     *
     * @param rpcConnection The URL of the Ethereum RPC endpoint.
     * @param receipt The receipt of the transaction.
     * @return The JSON representation of the transaction receipt, or null if not found.
     */
    fun getTransactionReceipt(rpcConnection: String, receipt: String): String? {
        val resp = evmConnector.send(rpcConnection, "eth_getTransactionReceipt", listOf(receipt))
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

    /**
     * Send a transaction to the Ethereum network.
     *
     * @param rpcConnection The URL of the Ethereum RPC endpoint.
     * @param contractAddress The address of the smart contract.
     * @param payload The payload data for the transaction.
     * @return The receipt of the transaction.
     */
    private fun sendTransaction(rpcConnection: String, contractAddress: String, payload: String): String {
            println("Fetching Count")
            // Do this async
            val nonce = getTransactionCount(rpcConnection, "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73")
            println("NONCE: $nonce")
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

    private fun handleRequest(request: EvmRequest, respFuture: CompletableFuture<EvmResponse>) {
        log.info(request.schema.toString(true))
        // Parameters for the transaction/query
        val contractAddress = request.contractAddress
        val rpcConnection = request.rpcUrl
        val payload = request.payload
        val flowId = request.flowId
        val isTransaction = request.isTransaction


        println("REQUEST: $request")

        if (isTransaction) {
            // Transaction Being Sent
            val transactionOutput = sendTransaction(rpcConnection, contractAddress, payload)
            println("Transaction Output $transactionOutput")
            val result = EvmResponse(flowId, transactionOutput)
            respFuture.complete(result)
        } else {
            // Call Being Sent
            val callResult = sendCall(rpcConnection, contractAddress, payload)
            respFuture.complete(EvmResponse(flowId, callResult))
        }
    }



    override fun onNext(request: EvmRequest, respFuture: CompletableFuture<EvmResponse>) {

        try{
            handleRequest(request,respFuture)

        } catch (e: Throwable) {
            // Better error handling => Meaningful
            println("AT THIS ERROR")
            when(e){
                is java.net.ConnectException -> {
                    // try three more times, then complete exceptionally
                    var attempts = 0
                    while(attempts<3){
                        try {
                            handleRequest(request, respFuture)
                        }catch(error: Exception){
                            log.info("Failed on retry #${attempts}")
                        }
                        ++attempts
                    }
                    println("STARTING EXCEPTIONAL FAILURE")
                    respFuture.completeExceptionally(e)
                }
                else -> {
                    respFuture.completeExceptionally(e)
                }
            }
        }
    }
}
