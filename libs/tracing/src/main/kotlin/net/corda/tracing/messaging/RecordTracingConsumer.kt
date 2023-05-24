package net.corda.tracing.messaging

import brave.Span
import brave.Tracing
import brave.messaging.MessagingTracing
import brave.propagation.Propagation
import net.corda.messaging.api.records.Record

class RecordTracing(tracing: Tracing) {
    private val tracer = tracing.tracer()
    private val messagingTracing = MessagingTracing.create(tracing)
    private val recordHeaderGetter: Propagation.Getter<List<Pair<String, String>>, String> =
        Propagation.Getter<List<Pair<String, String>>, String> { request, key ->
            val iter = request.listIterator()
            // to guard against multiple (more recent) values for the same key we search
            // in reverse order
            while (iter.hasPrevious()){
                val item = iter.previous()
                if(item.first==key){
                     return@Getter item.second
                }
            }
            null
        }
    private val tracingContextExtractor = messagingTracing.propagation().extractor(recordHeaderGetter)

    fun nextSpan(record: Record<*, *>): Span {
        val extracted = tracingContextExtractor.extract(record.headers)
        val span = tracer.nextSpan(extracted)
        if (extracted.context() == null && !span.isNoop) {
           span.tag("kafka.topic", record.topic)
        }

        return span
    }
}

