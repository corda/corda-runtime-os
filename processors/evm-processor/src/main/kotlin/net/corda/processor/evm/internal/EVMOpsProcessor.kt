package net.corda.processor.evm.internal

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.EvmResponse
import net.corda.data.interop.evm.request.Call
import net.corda.data.interop.evm.request.GetTransactionReceipt
import net.corda.data.interop.evm.request.Transaction
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.interop.evm.dispatcher.factory.DispatcherFactory
import net.corda.interop.evm.dispatcher.EvmDispatcher
import net.corda.interop.evm.EVMErrorException
import net.corda.interop.evm.EthereumConnector
import net.corda.interop.evm.EvmRPCCall

import okhttp3.OkHttpClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass


/**
 * EVMOpsProcessor is an implementation of the RPCResponderProcessor for handling Ethereum Virtual Machine (EVM) requests.
 * It allows executing smart contract calls and sending transactions on an Ethereum network.
 */
class EVMOpsProcessor(
    factory: DispatcherFactory,
    httpClient: OkHttpClient,
    config: SmartConfig
) : RPCResponderProcessor<EvmRequest, EvmResponse> {

    private var dispatcher: Map<KClass<*>, EvmDispatcher>

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private val transientEthereumErrorCodes = listOf(
            -32003
        )
    }

    private val maxRetries = config.getInt("maxRetryAttempts")
    private val retryDelayMs = config.getLong("maxRetryDelay")
    private val fixedThreadPool = Executors.newFixedThreadPool(config.getInt("threadPoolSize"))


    init {
        val evmConnector = EthereumConnector(EvmRPCCall(httpClient))
        dispatcher = mapOf(
            Call::class to factory.callDispatcher(evmConnector),
            GetTransactionReceipt::class to factory.getTransactionByReceiptDispatcher(evmConnector),
            Transaction::class to factory.sendRawTransactionDispatcher(evmConnector)
        )
    }

    // This is blocking
    // Make this asynchronous
    private fun handleRequest(request: EvmRequest, respFuture: CompletableFuture<EvmResponse>) {
        dispatcher[request.payload::class]
            ?.dispatch(request).apply {
                respFuture.complete(this)
            }
            ?: throw CordaRuntimeException("Unregistered EVM operation: ${request.payload.javaClass}")

    }


    /**
     * The Retry Policy is responsibly for retrying an ethereum call, given that the ethereum error is transient
     *
     * @param maxRetries The maximum amount of retires allowed for a given error.
     * @param delayMs The Ethereum address for which to retrieve the balance.
     * @return The balance of the specified Ethereum address as a string.
     */
    inner class RetryPolicy(private val maxRetries: Int, private val delayMs: Long) {
        fun execute(action: () -> Unit) {
            var retries = 0
            while (retries <= maxRetries) {
                try {
                    return action()
                } catch (e: EVMErrorException) {
                    if (e.errorResponse.error.code in transientEthereumErrorCodes) {
                        retries++
                        log.warn(e.message)
                        if (retries <= maxRetries) {
                            TimeUnit.MILLISECONDS.sleep(delayMs)
                        } else {
                            throw CordaRuntimeException(e.message)

                        }
                    } else {
                        throw CordaRuntimeException(e.message)
                    }
                }
            }
        }
    }


    override fun onNext(request: EvmRequest, respFuture: CompletableFuture<EvmResponse>) {

        fixedThreadPool.submit {
            try {
                RetryPolicy(maxRetries, retryDelayMs).execute {
                    handleRequest(request, respFuture)
                }
            } catch (e: Exception) {
                respFuture.completeExceptionally(e)
            }
        }

    }

}


