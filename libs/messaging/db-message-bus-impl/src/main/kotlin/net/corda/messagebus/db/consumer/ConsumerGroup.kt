package net.corda.messagebus.db.consumer

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Used to manage partitioning of consumers across a group.
 *
 * The API for this class is thread-safe
 */
class ConsumerGroup(
    private val groupId: String,
    private val dbAccess: DBAccess,
) {

    companion object {
        const val NULL_GROUP_ID = "NULL"
    }

    /**
     * locks all access to this class.  Specifically to protect the three maps below
     */
    private val apiLock = ReentrantLock()
    private val topicPartitions: MutableMap<String, Set<CordaTopicPartition>> = ConcurrentHashMap()
    private val topicToConsumersMap: MutableMap<String, MutableSet<DBCordaConsumerImpl<*, *>>> = ConcurrentHashMap()
    private val partitionsPerConsumer: MutableMap<String, MutableSet<CordaTopicPartition>> = ConcurrentHashMap()

    internal fun getTopicPartitionsFor(consumer: DBCordaConsumerImpl<*, *>): Set<CordaTopicPartition> {
        return apiLock.withLock {
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
        apiLock.withLock {
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
        return apiLock.withLock {
            verifyValid()
            topicToConsumersMap.filter { consumer in it.value }.forEach { (topic, consumers) ->
                consumers.remove(consumer)
                getInternalPartitionListFor(consumer).clear()
                repartition(topic)
            }

        }
    }

    private fun repartition(topic: String) {
        val consumersToRepartition = getConsumersFor(topic)
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
            throw CordaMessageAPIFatalException("Cannot subscribe when consumer group is not set.")
        }
    }
}
