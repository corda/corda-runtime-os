package net.corda.messaging.kafka.utils

import net.corda.messaging.api.records.Record
import org.apache.kafka.clients.consumer.ConsumerRecord

fun <K: Any, V: Any> ConsumerRecord<K, V>.toRecord(): Record<K, V> {
    return Record(this.topic(), this.key(), this.value())
}