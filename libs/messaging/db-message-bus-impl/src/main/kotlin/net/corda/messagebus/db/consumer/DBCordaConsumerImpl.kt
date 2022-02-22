package net.corda.messagebus.db.consumer

import com.typesafe.config.Config
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.configuration.ConfigProperties
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.schema.registry.AvroSchemaRegistry
import java.nio.ByteBuffer
import java.time.Duration

@Suppress("TooManyFunctions", "LongParameterList")
class DBCordaConsumerImpl<K : Any, V : Any> internal constructor(
    consumerConfig: Config,
    private val dbAccess: DBAccess,
    private val consumerGroup: ConsumerGroup?,
    private val avroSchemaRegistry: AvroSchemaRegistry,
    private val kClazz: Class<K>,
    private val vClazz: Class<V>,
    private val onSerializationError: (ByteArray) -> Unit,
    private var defaultListener: CordaConsumerRebalanceListener?,
) : CordaConsumer<K, V> {

    enum class SubscriptionType { NONE, ASSIGNED, SUBSCRIBED }

    companion object {
        const val MAX_POLL_RECORDS = "max.poll.records"
        const val MAX_POLL_INTERVAL = "max.poll.interval.ms"
    }

    private val groupId = consumerConfig.getString(ConfigProperties.GROUP_ID)
    private val maxPollRecords: Int = consumerConfig.getInt(MAX_POLL_RECORDS)
    private val maxPollInterval: Long = consumerConfig.getLong(MAX_POLL_INTERVAL)
    private var topicPartitions: MutableSet<CordaTopicPartition> = mutableSetOf()
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
        topicPartitions = partitions.toMutableSet()
        subscriptionType = SubscriptionType.ASSIGNED
    }

    override fun assignment(): Set<CordaTopicPartition> {
        checkNotSubscribed()
        return topicPartitions.toSet()
    }

    override fun position(partition: CordaTopicPartition): Long {
        return lastReadOffset[partition]
            ?: throw CordaMessageAPIFatalException("No offset for $partition")
    }

    override fun seek(partition: CordaTopicPartition, offset: Long) {
        lastReadOffset[partition] = offset
    }

    override fun seekToBeginning(partitions: Collection<CordaTopicPartition>) {
        lastReadOffset.putAll(partitions.associateWith { 0L })
    }

    override fun seekToEnd(partitions: Collection<CordaTopicPartition>) {
        val maxOffsets = dbAccess.getMaxOffsetsPerTopicPartition()
        lastReadOffset.putAll(partitions.associateWith { maxOffsets[it] ?: 0L })
    }

    override fun beginningOffsets(partitions: Collection<CordaTopicPartition>): Map<CordaTopicPartition, Long> {
        TODO("Not yet implemented")
    }

    override fun endOffsets(partitions: Collection<CordaTopicPartition>): Map<CordaTopicPartition, Long> {
        return dbAccess.getMaxOffsetsPerTopicPartition().filter { it.key in partitions }
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
        return dbRecords.map { dbRecord ->
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
        val lastCommittedOffsets = dbAccess.getMaxCommittedOffset(groupId, topicPartitions)
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
        TODO("Not yet implemented")
    }

    override fun getPartitions(topic: String, timeout: Duration): List<CordaTopicPartition> {
        return topicPartitions.filter { it.topic == topic }
    }

    override fun close(timeout: Duration) {
        // Nothing to do here
    }

    override fun close() {
        close(Duration.ZERO)
    }

    override fun setDefaultRebalanceListener(defaultListener: CordaConsumerRebalanceListener) {
        this.defaultListener = defaultListener
    }

    fun getConsumerGroup(): String = groupId

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

        topicPartitions = newTopicPartitions.toMutableSet()
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
        return deserialize(bytes, kClazz)
            ?: throw CordaMessageAPIFatalException("Should never get null result from key deserialize")
    }

    private fun deserializeValue(bytes: ByteArray?): V? {
        return deserialize(bytes, vClazz)
    }

    private fun <T : Any> deserialize(bytes: ByteArray?, clazz: Class<T>): T? {
        if (bytes == null) {
            return null
        }

        return try {
            avroSchemaRegistry.deserialize(ByteBuffer.wrap(bytes), clazz, null)
        } catch (ex: Exception) {
            onSerializationError.invoke(bytes)
            throw ex
        }
    }

    private fun checkNotSubscribed() {
        if (subscriptionType == DBCordaConsumerImpl.SubscriptionType.SUBSCRIBED) {
            throw CordaMessageAPIFatalException("Consumer is already subscribed to topic(s)")
        }
    }

    private fun checkNotAssigned() {
        if (subscriptionType == DBCordaConsumerImpl.SubscriptionType.ASSIGNED) {
            throw CordaMessageAPIFatalException("Consumer is already assigned topic partitions")
        }
    }

}
