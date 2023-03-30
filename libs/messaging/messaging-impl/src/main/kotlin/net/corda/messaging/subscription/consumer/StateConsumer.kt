package net.corda.messaging.subscription.consumer

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.schema.Schemas.getStateAndEventStateTopic
import java.time.Duration
import javax.swing.plaf.nimbus.State

/**
 * A wrapper around a CordaConsumer for the state and event pattern that converts any provided partitions to the
 * relevant state topic instead.
 */
class StateConsumer<K: Any, V: Any>(private val consumer: CordaConsumer<K, V>) {

    private companion object {
        private const val STATE_TOPIC_SUFFIX = ".state"
    }

    fun assign(partitions: Collection<CordaTopicPartition>) {
        consumer.assign(partitions.toStatePartitions())
    }

    fun beginningOffsets(partitions: Collection<CordaTopicPartition>): Map<CordaTopicPartition, Long> {
        if (partitions.isEmpty()) {
            return emptyMap()
        }
        // Assume the topic is the same for all partitions.
        val topic = partitions.first().topic
        return consumer.beginningOffsets(partitions.toStatePartitions()).mapKeys {
            CordaTopicPartition(topic, it.key.partition)
        }
    }

    fun endOffsets(partitions: Collection<CordaTopicPartition>): Map<CordaTopicPartition, Long> {
        if (partitions.isEmpty()) {
            return emptyMap()
        }
        // Assume the topic is the same for all partitions.
        val topic = partitions.first().topic
        return consumer.endOffsets(partitions.toStatePartitions()).mapKeys {
            CordaTopicPartition(topic, it.key.partition)
        }
    }

    fun poll(timeout: Duration): List<CordaConsumerRecord<K, V>> {
        return consumer.poll(timeout)
    }

    fun assignment(): Set<CordaTopicPartition> {
        return consumer.assignment().mapTo(mutableSetOf()) {
            CordaTopicPartition(it.topic.removeSuffix(STATE_TOPIC_SUFFIX), it.partition)
        }
    }

    fun paused(): Set<CordaTopicPartition> {
        return consumer.paused().mapTo(mutableSetOf()) {
            CordaTopicPartition(it.topic.removeSuffix(STATE_TOPIC_SUFFIX), it.partition)
        }
    }



    private fun Collection<CordaTopicPartition>.toStatePartitions(): Collection<CordaTopicPartition> {
        return this.map { CordaTopicPartition(getStateAndEventStateTopic(it.topic), it.partition) }
    }
}