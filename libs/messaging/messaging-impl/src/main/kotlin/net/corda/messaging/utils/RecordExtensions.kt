package net.corda.messaging.utils

import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record

fun <K : Any, V : Any> CordaConsumerRecord<K, V>.toRecord(): Record<K, V> {
    return Record(
        this.topic,
        this.key,
        this.value,
        this.timestamp,
        this.headers
    )
}

fun <K : Any, V : Any> CordaConsumerRecord<K, V>.toEventLogRecord(): EventLogRecord<K, V> {
    return EventLogRecord(
        this.topic,
        this.key,
        this.value,
        this.partition,
        this.offset,
        this.timestamp,
        this.headers
    )
}

fun <K: Any, V: Any> EventLogRecord<K, V>.toRecord(): Record<K, V> {
    return Record(
        topic = this.topic,
        key = this.key,
        value = this.value,
        timestamp=this.timestamp,
        headers = this.headers
    )
}

fun Record<*, *>.toCordaProducerRecord(): CordaProducerRecord<*, *> {
    val topic = this.topic
    requireNotNull(topic) { "Topic is not allowed to be null for CordaProducerRecords" }
    return CordaProducerRecord(topic, this.key, this.value, this.headers)
}

fun List<Record<*, *>>.toCordaProducerRecords(): List<CordaProducerRecord<*, *>> {
    return this.map { it.toCordaProducerRecord() }
}
