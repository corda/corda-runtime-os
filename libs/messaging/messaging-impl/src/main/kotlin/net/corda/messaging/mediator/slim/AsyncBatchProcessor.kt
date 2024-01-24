package net.corda.messaging.mediator.slim

import net.corda.tracing.wrapWithTracingExecutor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class AsyncBatchProcessor<REQUEST, RESPONSE>(
    private val batchHandler: (Collection<RequestItem<REQUEST, RESPONSE>>) -> Unit,
    private val maxBatchSize: Int = Int.MAX_VALUE,
) {

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    // We use a limited queue executor to ensure we only ever queue one new request if we are currently processing
    // an existing request.
    private val executor = wrapWithTracingExecutor(
        ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            LinkedBlockingQueue(1),
            ThreadPoolExecutor.DiscardPolicy(),
        ),
    )

    data class RequestItem<REQUEST, RESPONSE>(
        val request: REQUEST,
        val requestFuture: CompletableFuture<RESPONSE>,
    )

    private val requestQueue = LinkedBlockingQueue<RequestItem<REQUEST, RESPONSE>>()

    fun enqueueRequest(request: REQUEST): CompletableFuture<RESPONSE> {
        val requestCompletion = CompletableFuture<RESPONSE>()
        requestQueue.add(RequestItem(request, requestCompletion))
        CompletableFuture.runAsync(::drainAndProcessQueue, executor)
        return requestCompletion
    }

    private fun drainAndProcessQueue() {
        var requests = drainQueuedRequests()

        while (requests.isNotEmpty()) {
            try {
                batchHandler(requests)
            } catch (e: Exception) {
                log.error("Unexpected exception while executing batch handler", e)
            }

            // look for more request to process
            requests = drainQueuedRequests()
        }
    }

    private fun drainQueuedRequests(): List<RequestItem<REQUEST, RESPONSE>> {
        return mutableListOf<RequestItem<REQUEST, RESPONSE>>().apply {
            requestQueue.drainTo(this, maxBatchSize)
        }
    }
}
