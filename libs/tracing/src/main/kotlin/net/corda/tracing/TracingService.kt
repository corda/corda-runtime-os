package net.corda.tracing

import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.Producer
import java.util.concurrent.ExecutorService
import javax.servlet.Filter

interface TracingService : AutoCloseable {

    fun addTraceHeaders(headers: List<Pair<String, String>>): List<Pair<String, String>>

    fun <R> nextSpan(operationName: String, processingBlock: TraceContext.() -> R): R

    fun <R> nextSpan(operationName: String, record: Record<*, *>, processingBlock: TraceContext.() -> R): R

    fun <R> nextSpan(operationName: String, record: EventLogRecord<*, *>, processingBlock: TraceContext.() -> R): R

    fun wrapWithTracingExecutor(executor: ExecutorService): ExecutorService

    fun <K, V> wrapWithTracingProducer(kafkaProducer: Producer<K, V>): Producer<K, V>

    fun <K, V> wrapWithTracingConsumer(kafkaConsumer: Consumer<K, V>): Consumer<K, V>

    fun getTracedServletFilter(): Filter

    fun traceBatch(operationName: String): BatchRecordTracer
}

