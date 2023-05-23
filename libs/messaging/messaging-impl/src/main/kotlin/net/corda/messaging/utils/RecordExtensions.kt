package net.corda.messaging.utils

import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.tracing.NoopRecordTraceContext
import net.corda.messaging.tracing.RecordTracingContextImpl

fun <K : Any, V : Any> CordaConsumerRecord<K, V>.toRecord(): Record<K, V> {
    val tracingContext = this.tracing?.let {
        RecordTracingContextImpl(it)
    } ?: NoopRecordTraceContext()

    return Record(
        this.topic,
        this.key,
        this.value,
        this.headers,
        tracingContext
    )
}

fun <K : Any, V : Any> CordaConsumerRecord<K, V>.toEventLogRecord(): EventLogRecord<K, V> {
    val tracingContext = this.tracing?.let {
        RecordTracingContextImpl(it)
    } ?: NoopRecordTraceContext()

    return EventLogRecord(
        this.topic,
        this.key,
        this.value,
        this.partition,
        this.offset,
        tracingContext
    )
}

fun <K: Any, V: Any> EventLogRecord<K,V>.toRecord():Record<K,V>{
    return Record(
        topic = this.topic,
        key = this.key,
        value = this.value,
        tracing = this.tracing
    )
}

fun Record<*, *>.toCordaProducerRecord(): CordaProducerRecord<*, *> {
    return CordaProducerRecord(this.topic, this.key, this.value, this.headers)
}

fun List<Record<*, *>>.toCordaProducerRecords(): List<CordaProducerRecord<*, *>> {
    return this.map { it.toCordaProducerRecord() }
}
