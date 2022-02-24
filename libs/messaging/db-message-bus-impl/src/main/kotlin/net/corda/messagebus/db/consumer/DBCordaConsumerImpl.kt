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
import net.corda.messagebus.db.datamodel.CommittedOffsetEntry
import net.corda.messagebus.db.datamodel.TransactionState
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messagebus.db.producer.CordaAtomicDBProducerImpl.Companion.ATOMIC_TRANSACTION
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

@Suppress("TooManyFunctions", "LongParameterList")
class DBCordaConsumerImpl<K : Any, V : Any> internal constructor(
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
    private var topicPartitions: Set<CordaTopicPartition> = emptySet()
    private val pausedPartitions: MutableSet<CordaTopicPartition> = mutableSetOf()
    private val partitionListeners: MutableMap<String, CordaConsumerRebalanceListener?> = mutableMapOf()
    private var currentTopicPartition = topicPartitions.iterator()
    private var lastReadOffset = dbAccess.getMaxOffsetsPerTopicPartition().toMutableMap()
    private var subscriptionType = SubscriptionType.NONE

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

    override fun position(partition: CordaTopicPartition): Long {
        return lastReadOffset[partition]
            ?: when (autoResetStrategy) {
                CordaOffsetResetStrategy.EARLIEST -> beginningOffsets(setOf(partition)).values.single()
                CordaOffsetResetStrategy.LATEST -> endOffsets(setOf(partition)).values.single()
                else -> throw CordaMessageAPIFatalException("No offset for $partition")
            }
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
        return dbAccess.getMinCommittedOffsets(groupId, partitions.toSet())
    }

    override fun endOffsets(partitions: Collection<CordaTopicPartition>): Map<CordaTopicPartition, Long> {
        return dbAccess.getMaxCommittedOffsets(groupId, partitions.toSet())
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
        val fromOffset = getNextOffsetFor(topicPartition)

        val dbRecords = dbAccess.readRecords(fromOffset, topicPartition, maxPollRecords)

        return dbRecords.takeWhile {
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
    }

    override fun resetToLastCommittedPositions(offsetStrategy: CordaOffsetResetStrategy) {
        val lastCommittedOffsets = dbAccess.getMaxCommittedOffsets(groupId, topicPartitions)
        val maxOffsets = dbAccess.getMaxOffsetsPerTopicPartition().toMutableMap()

        for (topicPartition in topicPartitions) {
            val backupOffset = if (offsetStrategy == CordaOffsetResetStrategy.LATEST) {
                maxOffsets[topicPartition] ?: 0L
            } else {
                0L
            }
            lastReadOffset[topicPartition] = lastCommittedOffsets[topicPartition] ?: backupOffset
        }
    }

    override fun commitSyncOffsets(event: CordaConsumerRecord<K, V>, metaData: String?) {
        dbAccess.writeOffsets(
            listOf(
                CommittedOffsetEntry(
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

        removedTopicPartitions.groupBy { it.topic }.forEach { (topic, newPartitions) ->
            partitionListeners[topic]?.onPartitionsRevoked(newPartitions)
                ?: defaultListener?.onPartitionsRevoked(newPartitions)
        }

        addedTopicPartitions.groupBy { it.topic }.forEach { (topic, newPartitions) ->
            partitionListeners[topic]?.onPartitionsAssigned(newPartitions)
                ?: defaultListener?.onPartitionsAssigned(newPartitions)
        }

        topicPartitions = newTopicPartitions
        currentTopicPartition = topicPartitions.iterator()
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

    private fun getNextOffsetFor(topicPartition: CordaTopicPartition): Long {
        val offset = position(topicPartition)
        lastReadOffset[topicPartition] = offset + 1
        return offset
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

    private fun CordaOffsetResetStrategy.from(strategy: String?): CordaOffsetResetStrategy {
        return if (strategy.isNullOrEmpty()) {
            CordaOffsetResetStrategy.NONE
        } else if (strategy.toLowerCase() == "earliest") {
            CordaOffsetResetStrategy.EARLIEST
        } else if (strategy.toLowerCase() == "latest") {
            CordaOffsetResetStrategy.LATEST
        } else {
            throw CordaMessageAPIFatalException("Invalid configuration option '$strategy' for $AUTO_OFFSET_RESET")
        }
    }
}
