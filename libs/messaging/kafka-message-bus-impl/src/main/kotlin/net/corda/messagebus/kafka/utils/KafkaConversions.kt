package net.corda.messagebus.kafka.utils

import net.corda.messagebus.api.CordaOffsetAndMetadata
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.consumer.OffsetResetStrategy

fun CordaOffsetResetStrategy.toKafka() = OffsetResetStrategy.valueOf(this.name)
fun CordaOffsetAndMetadata.toKafka() = OffsetAndMetadata(this.offset, this.metadata)

fun List<CordaConsumerRecord<*, *>>.toKafkaRecords():
        List<ConsumerRecord<*, *>> {
    return this.map { it.toKafkaRecord() }
}

fun CordaConsumerRecord<*, *>.toKafkaRecord():
        ConsumerRecord<*, *> {
    return ConsumerRecord(
        this.topic,
        this.partition,
        this.offset,
        this.key,
        this.value
    )
}

