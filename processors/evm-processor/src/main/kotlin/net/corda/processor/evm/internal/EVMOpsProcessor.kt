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
    private fun getTransactionReceipt(rpcConnection: String, receipt: String): String? {
        val resp = evmConnector.send(rpcConnection, "eth_getTransactionReceipt", listOf(receipt))
        return resp.result.toString()
    }

    /**
     * Get the Chain Id from the Ethereum node.
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
     * Query the completion status of a transaction using the Ethereum node.
     *
     * @param rpcConnection The URL of the Ethereum RPC endpoint.
     * @param transactionHash The hash of the transaction to query.
     * @return The JSON representation of the transaction receipt.
     */
    private fun queryCompletion(rpcConnection: String, transactionHash: String): String {
        val res = evmConnector.send(rpcConnection, "eth_getTransactionReceipt", listOf(transactionHash), true)
        return res.result.toString()
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
        val nonce = getTransactionCount(rpcConnection, "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73")
        val estimatedGas = estimateGas(
            rpcConnection,
            "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
            contractAddress,
            payload
        )

        // Use for pre EIP-1559 ones
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

        val signer = Credentials.create("0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63")
        println("Passed Credentials")

        val signed = TxSignServiceImpl(signer).sign(transaction, "1337".toLong())
        println("Passed Signing")
        println(Numeric.toHexString(signed))
        val tReceipt = evmConnector.send(rpcConnection, "eth_sendRawTransaction", listOf(Numeric.toHexString(signed))).result.toString()
        println("Receipt: ${tReceipt}")
        if (contractAddress.isEmpty()) {
            val transactionOutput = queryCompletionContract(rpcConnection, tReceipt)
            println("Transaction Details: ${transactionOutput}")
            return transactionOutput

        } else {
            val transactionOutput = queryCompletion(rpcConnection, tReceipt)
            println("Transaction Details: ${transactionOutput}")
        }
        return tReceipt
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

    override fun onNext(request: EvmRequest, respFuture: CompletableFuture<EvmResponse>) {
        log.info(request.schema.toString(true))
        // Parameters for the transaction/query
        val contractAddress = request.contractAddress
        val rpcConnection = request.rpcUrl
        val payload = request.payload
        val flowId = request.flowId
        val isTransaction = request.isTransaction

        try {
            if (isTransaction) {
                // Transaction Being Sent
                val transactionOutput = sendTransaction(rpcConnection, contractAddress, payload)

                val result = EvmResponse(flowId, transactionOutput)
                respFuture.complete(result)
            } else {
                // Call Being Sent
                val callResult = sendCall(rpcConnection, contractAddress, payload)
                respFuture.complete(EvmResponse(flowId, callResult))
            }
        } catch (e: Throwable) {
            // Better error handling => Meaningful
            println(e)
            println(e.message)
            respFuture.completeExceptionally(e)
        }
    }
}
