package net.corda.messagebus.db.consumer

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.configuration.ConfigProperties
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

class ConsumerGroup(
    private val groupId: String,
    private val dbAccess: DBAccess,
) {

    companion object {
        const val NULL_GROUP_ID = "NULL"
    }

    private val lock: ReentrantReadWriteLock = ReentrantReadWriteLock()
    private val topicPartitions: MutableMap<String, MutableSet<CordaTopicPartition>> = mutableMapOf()
    private val consumerMap: MutableMap<String, MutableSet<CordaConsumer<*, *>>> = mutableMapOf()
    private val partitionsPerConsumer: MutableMap<Int, MutableSet<CordaTopicPartition>> = mutableMapOf()

    fun getTopicPartitionsFor(consumer: CordaConsumer<*, *>): Set<CordaTopicPartition> {
        verifyValid()
        return partitionsPerConsumer[consumer.hashCode()]
            ?: throw CordaMessageAPIFatalException("Consumer not part of consumer group $groupId")
    }

    private fun getConsumersFor(topic: String) = consumerMap.computeIfAbsent(topic) { mutableSetOf() }
    private fun getInternalPartitionListFor(consumer: CordaConsumer<*, *>) =
        partitionsPerConsumer.computeIfAbsent(consumer.hashCode()) { mutableSetOf() }

    fun subscribe(
        consumer: CordaConsumer<*, *>,
        topics: Collection<String>,
    ) {
        verifyValid()
        topics.forEach { topic ->
            buildTopicPartitionsFor(topic)
            getConsumersFor(topic).add(consumer)
            try {
                repartition(topic)
            } catch (ex: Exception) {
                throw CordaMessageAPIFatalException("Cannot subscribe to topic $topic", ex)
            }
        }
    }

    private fun repartition(topic: String) {
        lock.write {
            val consumersToRepartition = consumerMap[topic]
                ?: throw CordaMessageAPIFatalException("Internal error.  Topic for consumers should exist.")
            val topicPartitionsToUpdate = topicPartitions[topic]
                ?: throw CordaMessageAPIFatalException("Topic $topic not available. Has it been added correctly?")

            consumersToRepartition.forEach { getInternalPartitionListFor(it).clear() }

            var consumerIterator = consumersToRepartition.iterator()
            topicPartitionsToUpdate.forEach { topicPartition ->
                if (!consumerIterator.hasNext()) {
                    consumerIterator = consumersToRepartition.iterator()
                }
                getInternalPartitionListFor(consumerIterator.next()).add(topicPartition)
            }
        }
    }

    private fun buildTopicPartitionsFor(topic: String) {
        dbAccess.getTopicPartitionMapFor(topic).apply {
            repeat(numPartitions) { partition ->
                topicPartitions.computeIfAbsent(this.topic) { mutableSetOf() }.add(CordaTopicPartition(topic, partition))
            }
        }
    }

    private fun verifyValid() {
        if (groupId == NULL_GROUP_ID) {
            throw CordaMessageAPIFatalException("Cannot subscribe when '${ConfigProperties.GROUP_ID}' is not configured.")
        }
    }
}
