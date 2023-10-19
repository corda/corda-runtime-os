package net.corda.processor.evm.internal

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import net.corda.data.flow.event.FlowEvent
import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.request.Call
import net.corda.data.interop.evm.request.GetTransactionReceipt
import net.corda.data.interop.evm.request.Transaction
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.interop.evm.EVMErrorException
import net.corda.interop.evm.EthereumConnector
import net.corda.interop.evm.EvmRPCCall
import net.corda.interop.evm.dispatcher.EvmDispatcher
import net.corda.interop.evm.dispatcher.factory.DispatcherFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.getIntOrDefault
import net.corda.libs.configuration.getLongOrDefault
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.exceptions.CordaRuntimeException
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
    config: SmartConfig,
    private val externalEventResponseFactory: ExternalEventResponseFactory,
) : DurableProcessor<String, EvmRequest> {

    private var dispatcher: Map<KClass<*>, EvmDispatcher>

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private val transientEthereumErrorCodes = listOf(
            -32003
        )
    }

    private val maxRetries = config.getIntOrDefault("maxRetryAttempts", 3)
    private val retryDelayMs = config.getLongOrDefault("maxRetryDelay", 3000)
    private val fixedThreadPool = Executors.newFixedThreadPool(config.getIntOrDefault("threadPoolSize", 3))


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
    private fun handleRequest(request: EvmRequest): Record<String, FlowEvent> {
        val response = dispatcher[request.payload::class]?.dispatch(request)
        return if (response != null) {
            externalEventResponseFactory.success(request.flowExternalEventContext, response)
        } else {
            externalEventResponseFactory.platformError(
                request.flowExternalEventContext,
                CordaRuntimeException("Unregistered EVM operation: ${request.payload.javaClass}")
            )
        }
    }

    /**
     * The Retry Policy is responsibly for retrying an ethereum call, given that the ethereum error is transient
     *
     * @param maxRetries The maximum amount of retires allowed for a given error.
     * @param delayMs The Ethereum address for which to retrieve the balance.
     * @return The balance of the specified Ethereum address as a string.
     */
    inner class RetryPolicy(private val maxRetries: Int, private val delayMs: Long) {
        fun <T> execute(action: () -> T): T {
            var lastException: Exception? = null
            repeat(maxRetries) {
                try {
                    return action()
                } catch (e: EVMErrorException) {
                    lastException = e
                    if (e.errorResponse.error.code in transientEthereumErrorCodes) {
                        log.warn(e.message)
                        TimeUnit.MILLISECONDS.sleep(delayMs)
                    } else {
                        throw CordaRuntimeException(e.message)
                    }
                }
            }
            throw CordaRuntimeException("Reached max retries for EvmRequest", lastException)
        }
    }


    override fun onNext(events: List<Record<String, EvmRequest>>): List<Record<*, *>> {
        return events
                .filter { it.value != null }
                .mapNotNull {
                    try {
                        val request = it.value!!
                        RetryPolicy(maxRetries, retryDelayMs).execute {
                            handleRequest(request)
                        }
                    } catch (e: Exception) {
                        externalEventResponseFactory.platformError(
                            it.value!!.flowExternalEventContext,
                            CordaRuntimeException("Unexpected error while processing the flow", e)
                        )
                        null
                    }
                }
    }

    override val keyClass: Class<String> = String::class.java
    override val valueClass: Class<EvmRequest> = EvmRequest::class.java

}


