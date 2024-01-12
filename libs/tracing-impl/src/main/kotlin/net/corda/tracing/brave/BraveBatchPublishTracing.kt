package net.corda.tracing.brave

import brave.Span
import brave.Tracer
import net.corda.tracing.BatchPublishTracing

class BraveBatchPublishTracing(
    private val clientId: String,
    private val tracer: Tracer,
    private val recordExtractor: BraveRecordExtractor,
) : BatchPublishTracing {
    private val batchSpans = mutableListOf<Span>()

    override fun begin(recordHeaders: List<List<Pair<String,String>>>) {
        // Ensure any repeat calls to begin abandon any existing spans
        batchSpans.forEach { it.abandon() }
        batchSpans.clear()

        // We are going to group all the messages in the batch by their trace id (if they have one)
        // We then create a span for each sub batch to ensure we maintain the parent child relationships of the trace as
        // the incoming batch can contain messages with different trace contexts.
        batchSpans.addAll(
            recordHeaders.map(recordExtractor::extract)
            .filter { it.context() != null }
            .groupBy { ctx ->
                ctx.context().traceId()
            }.map { (_, traceContexts) ->
                tracer.nextSpan(traceContexts.first())
                    .name("Send Batch - $clientId")
                    .tag("send.client.id",clientId)
                    .tag("send.batch.size", traceContexts.size.toString())
                    .tag("send.batch.parent.size", recordHeaders.size.toString())
                    .start()
            }
        )
    }

    override fun complete() {
        batchSpans.forEach { it.finish() }
    }

    override fun abort() {
        batchSpans.forEach { it.abandon() }
    }
}