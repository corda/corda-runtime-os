package net.corda.messaging.kafka.utils

import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import org.apache.kafka.clients.consumer.ConsumerRecord

fun <K : Any, V : Any> ConsumerRecord<K, V>.toRecord(): Record<K, V> {
    return Record(this.topic(), this.key(), this.value())
}

fun <K : Any, V : Any> ConsumerRecord<K, V>.toEventLogRecord(): EventLogRecord<K, V> {
    return EventLogRecord(this.topic(), this.key(), this.value(), this.partition(), this.offset())
}