@file:ServiceConsumer(TracingServiceFactory::class, resolution = OPTIONAL)
@file:Suppress("TooManyFunctions")

package net.corda.tracing

import aQute.bnd.annotation.Resolution.OPTIONAL
import aQute.bnd.annotation.spi.ServiceConsumer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.tracing.impl.TracingState
import java.util.ServiceLoader
import java.util.concurrent.ExecutorService

/**
 * Configures the tracing for a given Corda worker.
 * If the zipkin host parameter is not set then tracing will be disabled
 *
 * @param serviceName The service name that will be displayed in dashboards.
 * @param zipkinHost The url of the zipkin host the trace data will be sent to. The value should include a port number
 * if the server is listening on the default 9411 port. Example value: http://localhost:9411
 *
 */
fun configureTracing(serviceName: String, zipkinHost: String?, samplesPerSecond: String?) {
    ServiceLoader.load(TracingServiceFactory::class.java, TracingService::class.java.classLoader).single().also {
        TracingState.currentTraceService = it.create(serviceName, zipkinHost, samplesPerSecond)
    }
}

fun wrapWithTracingExecutor(executor: ExecutorService): ExecutorService {
    return TracingState.currentTraceService.wrapWithTracingExecutor(executor)
}

fun traceBatch(operationName: String): BatchRecordTracer {
    return TracingState.currentTraceService.traceBatch(operationName)
}

fun <R> trace(operationName: String, processingBlock: TraceContext.() -> R): R {
    return TracingState.currentTraceService.nextSpan(operationName, processingBlock)
}

fun getOrCreateBatchPublishTracing(clientId: String): BatchPublishTracing {
    return TracingState.currentTraceService.getOrCreateBatchPublishTracing(clientId)
}

fun addTraceContextToRecords(records: List<Record<*, *>>): List<Record<*, *>> = records.map(::addTraceContextToRecord)

fun addTraceContextToRecord(it: Record<*, *>): Record<out Any, out Any> {
    return it.copy(headers = TracingState.currentTraceService.addTraceHeaders(it.headers))
}

fun addTraceContextToRecord(it: CordaProducerRecord<*, *>): CordaProducerRecord<out Any, out Any> {
    return it.copy(headers = TracingState.currentTraceService.addTraceHeaders(it.headers))
}

fun traceSend(
    headers: List<Pair<String,String>>,
    operationName: String
): TraceContext {
    return TracingState.currentTraceService.nextSpan(operationName, headers)
}

fun traceEventProcessing(
    event: Record<*, *>,
    operationName: String,
    processingBlock: () -> List<Record<*, *>>
): List<Record<*, *>> {
    return TracingState.currentTraceService.nextSpan(operationName, event) {
        addTraceContextToRecords(processingBlock())
    }
}

fun traceEventProcessing(
    event: EventLogRecord<*, *>,
    operationName: String,
    processingBlock: () -> List<Record<*, *>>
): List<Record<*, *>> {
    return TracingState.currentTraceService.nextSpan(operationName, event) {
        addTraceContextToRecords(processingBlock())
    }
}

fun traceEventProcessingNullableSingle(
    event: Record<*, *>,
    operationName: String,
    processingBlock: () -> Record<*, *>?
): Record<*, *>? {
    return TracingState.currentTraceService.nextSpan(operationName, event) {
        processingBlock()?.let { addTraceContextToRecord(it) }
    }
}

fun traceEventProcessingSingle(
    event: Record<*, *>,
    operationName: String,
    processingBlock: () -> Record<*, *>
): Record<*, *> {
    return TracingState.currentTraceService.nextSpan(operationName, event) {
        addTraceContextToRecord(processingBlock())
    }
}

fun <K : Any, S : Any, V : Any> traceStateAndEventExecution(
    event: Record<K, V>,
    operationName: String,
    processingBlock: TraceContext.() -> StateAndEventProcessor.Response<S>
): StateAndEventProcessor.Response<S> {
    return TracingState.currentTraceService.nextSpan(operationName, event) {
        val result = processingBlock(this)
        result.copy(responseEvents = addTraceContextToRecords(result.responseEvents))
    }
}

/**
 * Close tracing system, flushing buffers before shutdown.
 *
 * Call this method to avoid losing events at shutdown.
 */
fun shutdownTracing() {
    TracingState.currentTraceService.close()
}

/**
 * Configure Javalin to read trace IDs from requests or generate new ones if missing.
 */
fun configureJavalinForTracing(config: Any) {
    TracingState.currentTraceService.configureJavalin(config)
}
