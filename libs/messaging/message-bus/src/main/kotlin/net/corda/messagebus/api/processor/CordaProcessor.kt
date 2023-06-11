package net.corda.messagebus.api.processor

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.producer.CordaProducerRecord

interface CordaProcessor<K : Any, V : Any> : AutoCloseable {

    fun subscribe(topic: String, rebalanceListener: CordaConsumerRebalanceListener? = null)

    fun assign(partitions: Collection<CordaTopicPartition>)

    fun assignment(): Set<CordaTopicPartition>

    fun beginningOffsets(partitions: Collection<CordaTopicPartition>): Map<CordaTopicPartition, Long>

    fun endOffsets(partitions: Collection<CordaTopicPartition>): Map<CordaTopicPartition, Long>

    fun poll(userFunction: (CordaConsumerRecord<K, V>) -> Unit)

    fun pollAndProduceMany(userFunction: (CordaConsumerRecord<K, V>) -> List<CordaProducerRecord<*, *>>)

}