package net.corda.messagebus.kafka.tracing

import brave.Span
import net.corda.messagebus.api.tracing.CordaRecordTraceSpan
import net.corda.messagebus.api.tracing.CordaRecordTracingContext
import net.corda.tracing.impl.TracingState
import org.apache.kafka.clients.consumer.ConsumerRecord

class KafkaRecordTracingContext(private val kafkaRecord: ConsumerRecord<Any, Any>) : CordaRecordTracingContext {

    override fun nextSpan(operationName: String): CordaRecordTraceSpan {
        return KafkaRecordTraceSpan(TracingState.kafkaTracing.nextSpan(kafkaRecord).name(operationName))
    }

    override fun <T> recordNextSpan(operationName: String, processingBlock: () -> T): T {
        val span = nextSpan(operationName)
        span.start()
        return try {
            processingBlock()
        } catch (e: Exception) {
            span.error(e)
            throw e
        } finally {
            span.finish()
        }
    }

    private class KafkaRecordTraceSpan(private val span: Span) : CordaRecordTraceSpan {
        override fun start() {
            span.start()
        }

        override fun error(exception: Exception) {
            span.error(exception)
        }

        override fun finish() {
            span.finish()
        }
    }
}