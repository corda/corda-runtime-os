package net.corda.messagebus.db.consumer

import com.typesafe.config.Config
import net.corda.data.CordaAvroDeserializer
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.configuration.ConfigProperties
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.CLIENT_ID
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy
import net.corda.messagebus.db.datamodel.CommittedPositionEntry
import net.corda.messagebus.db.datamodel.TransactionState
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messagebus.db.producer.CordaAtomicDBProducerImpl.Companion.ATOMIC_TRANSACTION
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

@Suppress("TooManyFunctions", "LongParameterList")
internal class DBCordaConsumerImpl<K : Any, V : Any> constructor(
    private val consumerConfig: Config,
    private val dbAccess: DBAccess,
    private val consumerGroup: ConsumerGroup?,
    private val keyDeserializer: CordaAvroDeserializer<K>,
    private val valueDeserializer: CordaAvroDeserializer<V>,
    private var defaultListener: CordaConsumerRebalanceListener?,
) : CordaConsumer<K, V> {

    enum class SubscriptionType { NONE, ASSIGNED, SUBSCRIBED }

    companion object {
        const val MAX_POLL_RECORDS = "max.poll.records"
        const val MAX_POLL_INTERVAL = "max.poll.interval.ms"
        const val AUTO_OFFSET_RESET = "auto.offset.reset"
    }

    private val log: Logger = LoggerFactory.getLogger(consumerConfig.getString(CLIENT_ID))

    private val groupId = consumerConfig.getString(ConfigProperties.GROUP_ID)
    private val maxPollRecords: Int = consumerConfig.getInt(MAX_POLL_RECORDS)
    private val maxPollInterval: Long = consumerConfig.getLong(MAX_POLL_INTERVAL)
    private val autoResetStrategy =
        CordaOffsetResetStrategy.valueOf(consumerConfig.getString(AUTO_OFFSET_RESET).toUpperCase())
    private var subscriptionType = SubscriptionType.NONE

    private var topicPartitions = emptySet<CordaTopicPartition>()
    private var currentTopicPartition = topicPartitions.iterator()
    private val pausedPartitions = mutableSetOf<CordaTopicPartition>()
    private val partitionListeners = mutableMapOf<String, CordaConsumerRebalanceListener?>()
    private val lastReadOffset = mutableMapOf<CordaTopicPartition, Long>()

    override fun subscribe(topics: Collection<String>, listener: CordaConsumerRebalanceListener?) {
        checkNotAssigned()
        if (consumerGroup == null) {
            throw CordaMessageAPIFatalException("Cannot subscribe when '${ConfigProperties.GROUP_ID}' is not configured.")
        }
        consumerGroup.subscribe(this, topics)
        topics.forEach { partitionListeners[it] = listener }
        subscriptionType = SubscriptionType.SUBSCRIBED
    }

    override fun subscribe(topic: String, listener: CordaConsumerRebalanceListener?) {
        subscribe(listOf(topic), listener)
    }

    override fun assign(partitions: Collection<CordaTopicPartition>) {
        checkNotSubscribed()
        topicPartitions = partitions.toSet()
        subscriptionType = SubscriptionType.ASSIGNED
    }

    override fun assignment(): Set<CordaTopicPartition> {
        return topicPartitions
    }

    private fun getAutoResetOffset(partition:CordaTopicPartition): Long {
        return when (autoResetStrategy) {
            CordaOffsetResetStrategy.EARLIEST -> beginningOffsets(setOf(partition)).values.single()
            CordaOffsetResetStrategy.LATEST -> endOffsets(setOf(partition)).values.single()
            else -> throw CordaMessageAPIFatalException("No offset for $partition")
        }
    }

    override fun position(partition: CordaTopicPartition): Long {
        return lastReadOffset[partition] ?: getAutoResetOffset(partition)
    }

    override fun seek(partition: CordaTopicPartition, offset: Long) {
        if (lastReadOffset.containsKey(partition)) {
            lastReadOffset[partition] = offset
        } else {
            throw CordaMessageAPIFatalException("Partition is not currently assigned to this consumer")
        }
    }

    override fun seekToBeginning(partitions: Collection<CordaTopicPartition>) {
        beginningOffsets(partitions).forEach { (partition, offset) -> seek(partition, offset) }
    }

    override fun seekToEnd(partitions: Collection<CordaTopicPartition>) {
        endOffsets(partitions).forEach { (partition, offset) -> seek(partition, offset) }
    }

    override fun beginningOffsets(partitions: Collection<CordaTopicPartition>): Map<CordaTopicPartition, Long> {
        return dbAccess.getEarliestRecordOffset(partitions.toSet())
    }

    override fun endOffsets(partitions: Collection<CordaTopicPartition>): Map<CordaTopicPartition, Long> {
        return dbAccess.getLatestRecordOffset(partitions.toSet())
    }

    override fun resume(partitions: Collection<CordaTopicPartition>) {
        pausedPartitions.removeAll(partitions.toSet())
    }

    override fun pause(partitions: Collection<CordaTopicPartition>) {
        pausedPartitions.addAll(partitions)
    }

    override fun paused(): Set<CordaTopicPartition> {
        return pausedPartitions
    }

    override fun poll(): List<CordaConsumerRecord<K, V>> {
        return poll(Duration.ofMillis(maxPollInterval))
    }

    override fun poll(timeout: Duration): List<CordaConsumerRecord<K, V>> {
        val topicPartition = getNextTopicPartition() ?: return emptyList()
        val fromOffset = position(topicPartition)

        val dbRecords = dbAccess.readRecords(fromOffset, topicPartition, maxPollRecords)

        val result = dbRecords.takeWhile {
            it.transactionId.state == TransactionState.COMMITTED
        }.map { dbRecord ->
            CordaConsumerRecord(
                dbRecord.topic,
                dbRecord.partition,
                dbRecord.recordOffset,
                deserializeKey(dbRecord.key),
                deserializeValue(dbRecord.value),
                dbRecord.timestamp.toEpochMilli()
            )
        }
        seek(topicPartition, result.last().offset + 1)
        return result
    }

    override fun resetToLastCommittedPositions(offsetStrategy: CordaOffsetResetStrategy) {
        val lastCommittedOffsets = dbAccess.getMaxCommittedPositions(groupId, topicPartitions)
        for (topicPartition in topicPartitions) {
            lastReadOffset[topicPartition] = lastCommittedOffsets[topicPartition]
                ?: getAutoResetOffset(topicPartition)
        }
    }

    override fun commitSyncOffsets(event: CordaConsumerRecord<K, V>, metaData: String?) {
        dbAccess.writeOffsets(
            listOf(
                CommittedPositionEntry(
                    event.topic,
                    groupId,
                    event.partition,
                    event.offset,
                    ATOMIC_TRANSACTION,
                )
            )
        )
    }

    override fun getPartitions(topic: String, timeout: Duration): List<CordaTopicPartition> {
        return topicPartitions.filter { it.topic == topic }
    }

    override fun close(timeout: Duration) {
        // Nothing to do here
        log.info("Closing logger for ${consumerConfig.getString(CLIENT_ID)}")
    }

    override fun close() {
        close(Duration.ZERO)
    }

    override fun setDefaultRebalanceListener(defaultListener: CordaConsumerRebalanceListener) {
        this.defaultListener = defaultListener
    }

    internal fun getConsumerGroup(): String = groupId

    private fun updateTopicPartitions() {
        if (consumerGroup == null) {
            // No consumer group means no rebalancing
            return
        }

        val newTopicPartitions = consumerGroup.getTopicPartitionsFor(this)
        val addedTopicPartitions = newTopicPartitions - topicPartitions
        val removedTopicPartitions = topicPartitions - newTopicPartitions

        if (addedTopicPartitions.isEmpty() && removedTopicPartitions.isEmpty()) {
            return
        }

        removedTopicPartitions.groupBy { it.topic }.forEach { (topic, removedPartitions) ->
            removedPartitions.forEach { lastReadOffset.remove(it) }
            revokePartitions(topic, removedPartitions)
        }

        addedTopicPartitions.groupBy { it.topic }.forEach { (topic, newPartitions) ->
            dbAccess.getMaxCommittedPositions(groupId, newPartitions.toSet()).forEach { (topicPartition, offset) ->
                lastReadOffset[topicPartition] = offset ?: getAutoResetOffset(topicPartition)
            }
            assignPartitions(topic, newPartitions)
        }

        topicPartitions = newTopicPartitions
        currentTopicPartition = topicPartitions.iterator()
    }

    private fun revokePartitions(topic: String, removedPartitions: Collection<CordaTopicPartition>) {
        partitionListeners[topic]?.onPartitionsRevoked(removedPartitions)
            ?: defaultListener?.onPartitionsRevoked(removedPartitions)
    }

    private fun assignPartitions(topic: String, newPartitions: Collection<CordaTopicPartition>) {
        partitionListeners[topic]?.onPartitionsAssigned(newPartitions)
            ?: defaultListener?.onPartitionsAssigned(newPartitions)
    }

    /**
     * Returns null when all partitions are paused
     *
     * Internal for testing
     */
    internal fun getNextTopicPartition(): CordaTopicPartition? {
        updateTopicPartitions()

        fun nextPartition() = if (!currentTopicPartition.hasNext()) {
            currentTopicPartition = topicPartitions.iterator()
            currentTopicPartition.next()
        } else {
            currentTopicPartition.next()
        }

        val first = nextPartition()
        var next = first

        while (pausedPartitions.contains(next)) {
            next = nextPartition()
            if (first == next) {
                return null
            }
        }
        return next
    }

    private fun deserializeKey(bytes: ByteArray): K {
        return keyDeserializer.deserialize(bytes)
            ?: throw CordaMessageAPIFatalException("Should never get null result from key deserialize")
    }

    private fun deserializeValue(bytes: ByteArray?): V? {
        return if (bytes != null) { valueDeserializer.deserialize(bytes) } else { null }
    }

    private fun checkNotSubscribed() {
        if (subscriptionType == SubscriptionType.SUBSCRIBED) {
            throw CordaMessageAPIFatalException("Cannot assign when consumer is already subscribed to topic(s)")
        }
    }

    private fun checkNotAssigned() {
        if (subscriptionType == SubscriptionType.ASSIGNED) {
            throw CordaMessageAPIFatalException("Cannot subscribed when consumer is already assigned topic partitions")
        }
    }
}
