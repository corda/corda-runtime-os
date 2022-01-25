package net.corda.messaging.utils

import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record

fun <K : Any, V : Any> CordaConsumerRecord<K, V>.toRecord(): Record<K, V> {
    return Record(this.topic, this.key, this.value)
}

fun <K : Any, V : Any> CordaConsumerRecord<K, V>.toEventLogRecord(): EventLogRecord<K, V> {
    return EventLogRecord(this.topic, this.key, this.value, this.partition, this.offset)
}

fun Record<*, *>.toCordaProducerRecord(): CordaProducerRecord<*, *> {
    return CordaProducerRecord(this.topic, this.key, this.value)
}

fun List<Record<*, *>>.toCordaProducerRecords(): List<CordaProducerRecord<*, *>> {
    return this.map { it.toCordaProducerRecord() }
}
