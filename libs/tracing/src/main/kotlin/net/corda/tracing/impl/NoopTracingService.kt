package net.corda.tracing.impl

import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.tracing.BatchPublishTracing
import net.corda.tracing.BatchRecordTracer
import net.corda.tracing.TraceContext
import net.corda.tracing.TracingService
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.Producer
import java.util.concurrent.ExecutorService
import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.FilterConfig
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse

@Suppress("UNUSED_PARAMETER")
class NoopTracingService : TracingService {

    class NoopTraceContext : TraceContext {
        override fun traceTag(key: String, value: String) {
        }

        override fun traceRequestId(requestId: String) {
        }

        override fun traceVirtualNodeId(vNodeId: String) {
        }
    }

    class NoopBatchRecordTracer : BatchRecordTracer {
        override fun startSpanFor(event: Record<*, *>, correlationId: String) {
        }

        override fun errorSpanFor(correlationId: String, error: Exception, outputRecord: Record<*, *>): Record<*, *> {
            return outputRecord
        }

        override fun completeSpanFor(correlationId: String, outputRecord: Record<*, *>): Record<*, *> {
            return outputRecord
        }
    }

    class NoopServletTraceFilter : Filter {
        override fun init(filterConfig: FilterConfig?) {
        }

        override fun doFilter(request: ServletRequest?, response: ServletResponse?, chain: FilterChain?) {
            chain?.doFilter(request, response)
        }

        override fun destroy() {
        }
    }

    class NoopBatchPublishTracing:BatchPublishTracing{
        override fun begin(recordHeaders: List<List<Pair<String,String>>>) {
        }

        override fun complete() {
        }

        override fun abort() {
        }
    }

    override fun addTraceHeaders(headers: List<Pair<String, String>>): List<Pair<String, String>> {
        return headers
    }

    override fun <R> nextSpan(operationName: String, processingBlock: TraceContext.() -> R): R {
        return processingBlock(NoopTraceContext())
    }

    override fun <R> nextSpan(operationName: String, record: Record<*, *>, processingBlock: TraceContext.() -> R): R {
        return processingBlock(NoopTraceContext())
    }

    override fun <R> nextSpan(
        operationName: String,
        record: EventLogRecord<*, *>,
        processingBlock: TraceContext.() -> R
    ): R {
        return processingBlock(NoopTraceContext())
    }

    override fun getOrCreateBatchPublishTracing(clientId: String): BatchPublishTracing {
        return NoopBatchPublishTracing()
    }

    override fun wrapWithTracingExecutor(executor: ExecutorService): ExecutorService {
        return executor
    }

    override fun <K, V> wrapWithTracingProducer(kafkaProducer: Producer<K, V>): Producer<K, V> {
        return kafkaProducer
    }

    override fun <K, V> wrapWithTracingConsumer(kafkaConsumer: Consumer<K, V>): Consumer<K, V> {
        return kafkaConsumer
    }

    override fun getTracedServletFilter(): Filter {
        return NoopServletTraceFilter()
    }

    override fun traceBatch(operationName: String): BatchRecordTracer {
        return NoopBatchRecordTracer()
    }

    override fun close() {
    }
}