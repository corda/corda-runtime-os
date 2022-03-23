package net.corda.messagebus.db.consumer

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.configuration.ConfigProperties
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import java.util.concurrent.ConcurrentHashMap
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
    private val topicPartitions: MutableMap<String, Set<CordaTopicPartition>> = ConcurrentHashMap()
    private val topicToConsumersMap: MutableMap<String, MutableSet<DBCordaConsumerImpl<*, *>>> = ConcurrentHashMap()
    private val partitionsPerConsumer: MutableMap<String, MutableSet<CordaTopicPartition>> = ConcurrentHashMap()

    internal fun getTopicPartitionsFor(consumer: DBCordaConsumerImpl<*, *>): Set<CordaTopicPartition> {
        return lock.write {
            verifyValid()
            partitionsPerConsumer[consumer.clientId]?.toSet()
                ?: throw CordaMessageAPIFatalException("Consumer not part of consumer group $groupId")
        }
    }

    private fun getConsumersFor(topic: String) = topicToConsumersMap.computeIfAbsent(topic) { mutableSetOf() }
    private fun getInternalPartitionListFor(consumer: DBCordaConsumerImpl<*, *>) =
        partitionsPerConsumer.computeIfAbsent(consumer.clientId) { ConcurrentHashMap.newKeySet() }

    internal fun subscribe(
        consumer: DBCordaConsumerImpl<*, *>,
        topics: Collection<String>,
    ) {
        lock.write {
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
    }

    internal fun unsubscribe(
        consumer: DBCordaConsumerImpl<*, *>,
    ) {
        return lock.write {
            verifyValid()
            topicToConsumersMap.filter { consumer in it.value }.forEach { (topic, consumers) ->
                consumers.remove(consumer)
                getInternalPartitionListFor(consumer).clear()
                repartition(topic)
            }

        }
    }

    private fun repartition(topic: String) {
        val consumersToRepartition = topicToConsumersMap[topic]
            ?: throw CordaMessageAPIFatalException("Internal error.  Topic for consumers should exist.")
        val topicPartitionsToUpdate = topicPartitions[topic]
            ?: throw CordaMessageAPIFatalException("Topic $topic) not available. Has it been added correctly?")

        if (consumersToRepartition.isEmpty()) {
            return
        }

        consumersToRepartition.forEach { getInternalPartitionListFor(it).clear() }


        var consumerIterator = consumersToRepartition.iterator()
        topicPartitionsToUpdate.forEach { topicPartition ->
            if (!consumerIterator.hasNext()) {
                consumerIterator = consumersToRepartition.iterator()
            }
            getInternalPartitionListFor(consumerIterator.next()).add(topicPartition)
        }
    }

    private fun buildTopicPartitionsFor(topic: String) {
        topicPartitions[topic] = dbAccess.getTopicPartitionMapFor(topic)
    }

    private fun verifyValid() {
        if (groupId == NULL_GROUP_ID) {
            throw CordaMessageAPIFatalException("Cannot subscribe when '${ConfigProperties.GROUP_ID}' is not configured.")
        }
    }
}
