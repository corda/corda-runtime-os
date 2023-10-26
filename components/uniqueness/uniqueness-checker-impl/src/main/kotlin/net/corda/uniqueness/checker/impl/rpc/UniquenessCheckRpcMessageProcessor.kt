package net.corda.uniqueness.checker.impl.rpc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.corda.data.flow.event.FlowEvent
import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.data.uniqueness.UniquenessCheckResultUnhandledExceptionAvro
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.messaging.api.processor.SyncRPCProcessor
import net.corda.uniqueness.checker.UniquenessChecker
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

/**
 * Processes messages received from the RPC calls, and responds using the external
 * events response API.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UniquenessCheckRpcMessageProcessor(
    private val uniquenessChecker: UniquenessChecker,
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    override val requestClass: Class<UniquenessCheckRequestAvro>,
    override val responseClass: Class<FlowEvent>,
) : SyncRPCProcessor<UniquenessCheckRequestAvro, FlowEvent> {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
    private val channel = Channel<UniquenessCheck>()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            processRequests(channel)
        }
    }

    data class UniquenessCheck(val request: UniquenessCheckRequestAvro,
                               val completeSignalChannel: Channel<FlowEvent>)

    private fun CoroutineScope.processRequests(channel: Channel<UniquenessCheck>) = launch {
        val buffer = mutableMapOf<UniquenessCheckRequestAvro, UniquenessCheck>()
        for(msg in channel) {
            buffer[msg.request] = msg
            // fill up the buffer as long as there's messages in the channel
            while(!channel.isEmpty && buffer.size <= 20) {
                channel.receive().also {
                    buffer[it.request] = it
                }
            }
            log.info("Processing buffer of ${buffer.size} verification requests (${buffer.map { it.key.flowExternalEventContext.requestId }})")
            processBatch(buffer)
            buffer.clear()
        }
    }

    override fun process(request: UniquenessCheckRequestAvro): CompletableFuture<FlowEvent> {
        return CompletableFuture<FlowEvent>().apply {
            val response = runBlocking {
                val signalChannel = Channel<FlowEvent>(1)
                channel.send(
                    UniquenessCheck(request, signalChannel)
                )
                // wait for request to be processed
                val response = signalChannel.receive()
                log.info("Respond ${request.flowExternalEventContext.requestId}: $response")
                response
            }
            this.complete(response)
        }
    }

    private suspend fun processBatch(checks: Map<UniquenessCheckRequestAvro, UniquenessCheck>) {
        uniquenessChecker.processRequests(checks.values.map { it.request }).forEach { (request, response) ->
            if (response.result is UniquenessCheckResultUnhandledExceptionAvro) {
                externalEventResponseFactory.platformError(
                    request.flowExternalEventContext,
                    (response.result as UniquenessCheckResultUnhandledExceptionAvro).exception
                )
            } else {
                externalEventResponseFactory.success(request.flowExternalEventContext, response)
            }.value?.also {
                log.info("Complete ${request.flowExternalEventContext.requestId}: $it")
                checks[request]?.completeSignalChannel?.trySend(it)
            }
        }
    }
}
