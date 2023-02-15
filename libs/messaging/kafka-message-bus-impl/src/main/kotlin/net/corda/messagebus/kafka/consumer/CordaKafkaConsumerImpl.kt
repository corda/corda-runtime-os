package net.corda.messagebus.kafka.consumer

import java.time.Duration
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkKey
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy
import net.corda.messagebus.kafka.config.ResolvedConsumerConfig
import net.corda.messagebus.kafka.utils.toCordaTopicPartition
import net.corda.messagebus.kafka.utils.toCordaTopicPartitions
import net.corda.messagebus.kafka.utils.toTopicPartition
import net.corda.messagebus.kafka.utils.toTopicPartitions
import net.corda.messaging.api.chunking.ConsumerChunkDeserializerService
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.v5.base.util.trace
import net.corda.v5.base.util.uncheckedCast
import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.InvalidOffsetException
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.PartitionInfo
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.AuthenticationException
import org.apache.kafka.common.errors.AuthorizationException
import org.apache.kafka.common.errors.FencedInstanceIdException
import org.apache.kafka.common.errors.InconsistentGroupProtocolException
import org.apache.kafka.common.errors.InterruptException
import org.apache.kafka.common.errors.TimeoutException
import org.apache.kafka.common.errors.WakeupException
import org.slf4j.LoggerFactory


/**
 * Wrapper for a Kafka Consumer.
 */
@Suppress("TooManyFunctions")
class CordaKafkaConsumerImpl<K : Any, V : Any>(
    private val config: ResolvedConsumerConfig,
    private val consumer: Consumer<Any, Any>,
    private var defaultListener: CordaConsumerRebalanceListener? = null,
    private val chunkDeserializerService: ConsumerChunkDeserializerService<K, V>,
    private val vClazz: Class<V>,
) : CordaConsumer<K, V> {

    private var currentAssignment = mutableSetOf<Int>()
    private val bufferedRecords = mutableMapOf<Int, List<ConsumerRecord<Any, Any>>>()

    companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun close() {
        try {
            consumer.close()
        } catch (ex: Exception) {
            log.error("CordaKafkaConsumer failed to close consumer from group ${config.group}.", ex)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun poll(timeout: Duration): List<CordaConsumerRecord<K, V>> {
        clearBuffersForUnassignedPartitions()
        val polledRecords = try {
            consumer.poll(timeout)
        } catch (ex: Exception) {
            when (ex) {
                is AuthorizationException,
                is AuthenticationException,
                is IllegalArgumentException,
                is IllegalStateException,
                is ArithmeticException,
                is FencedInstanceIdException,
                is InconsistentGroupProtocolException,
                is InvalidOffsetException,
                -> {
                    logErrorAndThrowFatalException("Error attempting to poll.", ex)
                }

                is WakeupException,
                is InterruptException,
                is KafkaException,
                -> {
                    logWarningAndThrowIntermittentException("Error attempting to poll.", ex)
                }

                else -> logErrorAndThrowFatalException("Unexpected error attempting to poll.", ex)
            }
        }

        val recordsToReturn = mutableListOf<CordaConsumerRecord<K, V>>()
        polledRecords.groupBy { it.partition() }.forEach { (partition, records) ->
            val bufferedRecords = bufferedRecords[partition] ?: emptyList()
            log.trace {
                "Taking  ${bufferedRecords.size} buffered records from partition $partition and adding them to the polled records" +
                        " of size ${records.size}"
            }
            recordsToReturn.addAll(parseRecords(partition, bufferedRecords.plus(records)))
        }

        return recordsToReturn.sortedBy { it.timestamp }
    }

    /**
     * Check to see if we have been unassigned any partitions and clear the buffered records due to partial read of a chunked record.
     * Note: this call does not communicate with kafka. it is a field in the KafkaConsumer which is updated within its poll() invocation
     * after rebalances.
     */
    private fun clearBuffersForUnassignedPartitions() {
        val assignment = consumer.assignment().map { it.partition() }.toMutableSet()
        if (assignment != currentAssignment) {
            val buffersToClear = currentAssignment.minus(assignment)
            buffersToClear.forEach { bufferedRecords.remove(it) }
            currentAssignment = assignment
        }
    }

    /**
     * Take a list of records for a specific partition and process them.
     * Assemble any complete chunks into their original value using ConsumerRecord metadata from the last chunk.
     * Buffer any records which were returned later than any incomplete chunks. This is to avoid data loss on a rebalance.
     *
     * @param partition the partition the records are from
     * @param polledRecords record to poll from the bus for this partition
     * @return Complete records safe to return from the consumer with an offset lower than any incomplete chunk records
     */
    private fun parseRecords(partition: Int, polledRecords: List<ConsumerRecord<Any, Any>>): List<CordaConsumerRecord<K, V>> {
        val completeRecords = mutableListOf<CordaConsumerRecord<K, V>>()
        //only stores current chunks for this partition on this poll
        val currentChunks = mutableMapOf<String, ChunksRead>()

        polledRecords.forEach { consumerRecord ->
            val value = consumerRecord.value()
            val key = consumerRecord.key()
            if (key is ChunkKey && vClazz != Chunk::class.java && value is Chunk) {
                log.trace {
                    "read chunk offset ${consumerRecord.offset()} and chunkid ${value.requestId} and partNumber ${
                        value
                            .partNumber
                    }"
                }
                getCompleteRecord(key, value, consumerRecord, currentChunks)?.let {
                    log.trace { "adding complete record with offset ${consumerRecord.offset()} and chunkid ${value.requestId}" }
                    completeRecords.add(it)
                }
            } else {
                completeRecords.add(
                    CordaConsumerRecord(
                        consumerRecord.topic().removePrefix(config.topicPrefix),
                        consumerRecord.partition(),
                        consumerRecord.offset(),
                        uncheckedCast(consumerRecord.key()),
                        uncheckedCast(consumerRecord.value()),
                        consumerRecord.timestamp(),
                    )
                )
            }
        }

        val readUpToOffset = bufferIncompleteAndGetReadToOffset(currentChunks, polledRecords, partition)
        log.trace { "readUpToOffset: $readUpToOffset for partition $partition" }
        return completeRecords.filter { it.offset < readUpToOffset }
    }

    /**
     * Take a chunk and assemble it into its original value if all chunks are received
     * Otherwise return null.
     * @param chunkKey chunk key for this chunk
     * @param chunk chunk received in this consumer record
     * @param consumerRecord consumerRecord for this chunk. Will be used to set offset and other metadata on the reassembled chunked record
     * @param currentChunks current chunks read so far on this partition
     */
    private fun getCompleteRecord(
        chunkKey: ChunkKey,
        chunk: Chunk,
        consumerRecord: ConsumerRecord<Any, Any>,
        currentChunks: MutableMap<String, ChunksRead>,
    ): CordaConsumerRecord<K, V>? {
        val chunksRead = addChunkToMap(currentChunks, chunkKey, chunk, consumerRecord.offset())

        //chunk ordering is guaranteed as chunks are always committed via transactions
        return if (chunk.checksum != null) {
            currentChunks.remove(chunk.requestId)
            chunkDeserializerService.assembleChunks(chunksRead.chunks)?.let {
                CordaConsumerRecord(
                    consumerRecord.topic().removePrefix(config.topicPrefix),
                    consumerRecord.partition(),
                    //use the last chunks offset as the completed record offset to ensure we do not reread chunks on sync
                    consumerRecord.offset(),
                    it.first,
                    it.second,
                    consumerRecord.timestamp(),
                )
            }
        } else {
            null
        }
    }

    /**
     * Get the offset to read up to for a single partition and buffer any records which should not yet be returned.
     * We should not return chunks which are incomplete to the message processor. This could lead to record loss when syncing new
     * partitions after a rebalance.
     * @param currentChunks The chunks read so far for a partition
     * @param polledRecords The records polled for this partition
     * @param partition The partition being read
     * @return the offset to which we can safely read up to.
     */
    private fun bufferIncompleteAndGetReadToOffset(
        currentChunks: MutableMap<String, ChunksRead>,
        polledRecords: List<ConsumerRecord<Any, Any>>,
        partition: Int,
    ): Long {
        return if (currentChunks.isNotEmpty()) {
            val earliestPartialChunkOffset = currentChunks.values.sortedBy { it.startOffset }.first().startOffset
            val recordsToBuffer = polledRecords.filter { it.offset() >= earliestPartialChunkOffset }
            bufferedRecords[partition] = recordsToBuffer
            earliestPartialChunkOffset
        } else {
            bufferedRecords.remove(partition)
            Long.MAX_VALUE
        }
    }

    /**
     * Buffer a chunk into the map [currentChunks]
     * @param currentChunks buffered chunks for this partition
     * @param chunkKey chunk key for the chunk to buffer
     * @param chunk chunk value to buffer
     * @param offset the consumer record offset of the first chunk.
     * @return Object to represent the chunks read so far for what was initially a single record
     */
    private fun addChunkToMap(
        currentChunks: MutableMap<String, ChunksRead>,
        chunkKey: ChunkKey,
        chunk: Chunk,
        offset: Long,
    ): ChunksRead {
        val chunksRead = currentChunks[chunk.requestId] ?: ChunksRead(mutableMapOf(), offset)
        chunksRead.chunks[chunkKey] = chunk
        currentChunks[chunk.requestId] = chunksRead
        return chunksRead
    }

    /**
     * Chunks read for a single set of chunks associated with single avro object
     * @param chunks the chunks read so far
     * @param startOffset The start offset on kafka for this set of chunks. Used to calculate records to pass to the message
     * pattern when poll invocation does not return all chunks in a single poll
     */
    data class ChunksRead(
        val chunks: MutableMap<ChunkKey, Chunk> = mutableMapOf(),
        val startOffset: Long,
    )

    override fun resetToLastCommittedPositions(offsetStrategy: CordaOffsetResetStrategy) {
        val committed = consumer.committed(consumer.assignment())
        for (assignment in consumer.assignment()) {
            val offsetAndMetadata = committed[assignment]
            when {
                offsetAndMetadata != null -> {
                    consumer.seek(assignment, offsetAndMetadata.offset())
                }

                offsetStrategy == CordaOffsetResetStrategy.LATEST -> {
                    consumer.seekToEnd(setOf(assignment))
                }

                else -> {
                    consumer.seekToBeginning(setOf(assignment))
                }
            }
        }
    }

    override fun commitSyncOffsets(event: CordaConsumerRecord<K, V>, metaData: String?) {
        val offsets = mutableMapOf<TopicPartition, OffsetAndMetadata>()
        val topicPartition = TopicPartition(config.topicPrefix + event.topic, event.partition)
        offsets[topicPartition] = OffsetAndMetadata(event.offset + 1, metaData)
        var attemptCommit = true

        while (attemptCommit) {
            try {
                consumer.commitSync(offsets)
                attemptCommit = false
            } catch (ex: Exception) {
                when (ex) {
                    is InterruptException,
                    is TimeoutException,
                    -> {
                        logWarningAndThrowIntermittentException("Failed to commitSync offsets for record $event.", ex)
                    }

                    is CommitFailedException,
                    is AuthenticationException,
                    is AuthorizationException,
                    is IllegalArgumentException,
                    is FencedInstanceIdException,
                    -> {
                        logErrorAndThrowFatalException(
                            "Error attempting to commitSync offsets for record $event.",
                            ex
                        )
                    }

                    else -> {
                        logErrorAndThrowFatalException(
                            "Unexpected error attempting to commitSync offsets " +
                                    "for record $event.", ex
                        )
                    }
                }
            }
        }
    }

    override fun subscribe(topic: String, listener: CordaConsumerRebalanceListener?) =
        subscribe(listOf(topic), listener)

    override fun subscribe(topics: Collection<String>, listener: CordaConsumerRebalanceListener?) {
        val newTopics = topics.map {
            if (!it.contains(config.topicPrefix)) {
                config.topicPrefix + it
            } else {
                it
            }
        }
        var attemptSubscription = true
        while (attemptSubscription) {
            try {
                subscribeToTopics(listener, newTopics)
                attemptSubscription = false
            } catch (ex: Exception) {
                val message = "CordaKafkaConsumer failed to subscribe a consumer from group ${config.group}."
                when (ex) {
                    is IllegalStateException -> {
                        logErrorAndThrowFatalException(
                            "$message. Consumer is already subscribed to this topic. Closing subscription.",
                            ex
                        )
                    }

                    is IllegalArgumentException -> {
                        logErrorAndThrowFatalException("$message. Illegal args provided. Closing subscription.", ex)
                    }

                    else -> {
                        logErrorAndThrowFatalException("$message. Unexpected error.", ex)
                    }
                }
            }
        }
    }

    /**
     * Subscribe this consumer to the topics. Apply rebalance [listener].
     * If no [listener] provided, use [defaultListener] if available.
     */
    private fun subscribeToTopics(
        listener: CordaConsumerRebalanceListener?,
        topics: Collection<String>,
    ) {
        when {
            listener != null -> {
                consumer.subscribe(topics, listener.toKafkaListener())
            }

            defaultListener != null -> {
                consumer.subscribe(topics, defaultListener?.toKafkaListener())
            }

            else -> {
                consumer.subscribe(topics)
            }
        }
    }


    override fun getPartitions(topic: String): List<CordaTopicPartition> {
        val listOfPartitions: List<PartitionInfo> = try {
            consumer.partitionsFor(config.topicPrefix + topic)
        } catch (ex: Exception) {
            when (ex) {
                is AuthenticationException,
                is AuthorizationException,
                -> {
                    logErrorAndThrowFatalException("Fatal error attempting to get partitions on topic $topic", ex)
                }

                is InterruptException,
                is WakeupException,
                is TimeoutException,
                is KafkaException,
                -> {
                    logWarningAndThrowIntermittentException(
                        "Intermittent error attempting to get partitions on topic $topic",
                        ex
                    )
                }

                else -> {
                    logErrorAndThrowFatalException("Unexpected error attempting to get partitions on topic $topic", ex)
                }
            }
        }
        // Safeguard, should never happen in newer versions of Kafka
            ?: logWarningAndThrowIntermittentException(
                "Partitions for topic $topic are null. " +
                        "Kafka may not have completed startup."
            )

        return listOfPartitions.map {
            CordaTopicPartition(it.topic().removePrefix(config.topicPrefix), it.partition())
        }
    }

    /**
     * Log error and throw [CordaMessageAPIFatalException]
     * @return Nothing, to allow compiler to know that this method won't return a value in the catch blocks of the above exception handling.
     */
    private fun logErrorAndThrowFatalException(errorMessage: String, ex: Exception): Nothing {
        log.error(errorMessage, ex)
        throw CordaMessageAPIFatalException(errorMessage, ex)
    }

    /**
     * Log warning and throw [CordaMessageAPIIntermittentException]
     * @return Nothing, to allow compiler to know that this method won't return a value in the catch blocks of the above exception handling.
     */
    private fun logWarningAndThrowIntermittentException(errorMessage: String, ex: Exception? = null): Nothing {
        log.warn(errorMessage, ex)
        throw CordaMessageAPIIntermittentException(errorMessage, ex)
    }

    override fun assign(partitions: Collection<CordaTopicPartition>) {
        try {
            consumer.assign(partitions.toTopicPartitions(config.topicPrefix))
        } catch (ex: Exception) {
            when (ex) {
                is ConcurrentModificationException -> {
                    logWarningAndThrowIntermittentException(
                        "Intermittent error attempting to assign.",
                        ex
                    )
                }

                is IllegalArgumentException,
                is IllegalStateException,
                -> {
                    logErrorAndThrowFatalException("Fatal error attempting to assign.", ex)
                }

                else -> {
                    logErrorAndThrowFatalException("Unexpected error attempting to resume.", ex)
                }
            }
        }
    }

    override fun assignment(): Set<CordaTopicPartition> {
        return try {
            consumer.assignment().toCordaTopicPartitions(config.topicPrefix).toSet()
        } catch (ex: Exception) {
            when (ex) {
                is IllegalStateException -> {
                    logErrorAndThrowFatalException("Fatal error attempting to get assignment.", ex)
                }

                is ConcurrentModificationException -> {
                    logWarningAndThrowIntermittentException(
                        "Intermittent error attempting to get assignment.",
                        ex
                    )
                }

                else -> {
                    logErrorAndThrowFatalException("Unexpected error attempting to get assignment.", ex)
                }
            }
        }
    }

    override fun position(partition: CordaTopicPartition): Long {
        return try {
            consumer.position(partition.toTopicPartition(config.topicPrefix))
        } catch (ex: Exception) {
            when (ex) {
                is AuthenticationException,
                is AuthorizationException,
                is InvalidOffsetException,
                is IllegalStateException,
                -> {
                    logErrorAndThrowFatalException("Fatal error attempting to get position.", ex)
                }

                is InterruptException,
                is WakeupException,
                is TimeoutException,
                is KafkaException,
                -> {
                    logWarningAndThrowIntermittentException(
                        "Intermittent error attempting to get position.",
                        ex
                    )
                }

                else -> {
                    logErrorAndThrowFatalException("Unexpected error attempting to get position.", ex)
                }
            }
        }
    }

    override fun seek(partition: CordaTopicPartition, offset: Long) {
        try {
            consumer.seek(partition.toTopicPartition(config.topicPrefix), offset)
        } catch (ex: Exception) {
            when (ex) {
                is IllegalArgumentException,
                is IllegalStateException,
                -> {
                    logErrorAndThrowFatalException("Fatal error attempting to get the first offset.", ex)
                }

                is ConcurrentModificationException -> {
                    logWarningAndThrowIntermittentException(
                        "Intermittent error attempting to get the first offset.",
                        ex
                    )
                }

                else -> {
                    logErrorAndThrowFatalException(
                        "Unexpected error attempting to get the first offset.",
                        ex
                    )
                }
            }
        }
    }

    override fun seekToBeginning(partitions: Collection<CordaTopicPartition>) {
        try {
            consumer.seekToBeginning(partitions.toTopicPartitions(config.topicPrefix))
        } catch (ex: Exception) {
            when (ex) {
                is IllegalArgumentException,
                is IllegalStateException,
                -> {
                    logErrorAndThrowFatalException("Fatal error attempting to get the first offset.", ex)
                }

                is ConcurrentModificationException -> {
                    logWarningAndThrowIntermittentException(
                        "Intermittent error attempting to get the first offset.",
                        ex
                    )
                }

                else -> {
                    logErrorAndThrowFatalException(
                        "Unexpected error attempting to get the first offset.",
                        ex
                    )
                }
            }
        }
    }

    override fun seekToEnd(partitions: Collection<CordaTopicPartition>) {
        try {
            consumer.seekToEnd(partitions.toTopicPartitions(config.topicPrefix))
        } catch (ex: Exception) {
            when (ex) {
                is IllegalArgumentException,
                is IllegalStateException,
                -> {
                    logErrorAndThrowFatalException("Fatal error attempting to get the first offset.", ex)
                }

                is ConcurrentModificationException -> {
                    logWarningAndThrowIntermittentException(
                        "Intermittent error attempting to get the first offset.",
                        ex
                    )
                }

                else -> {
                    logErrorAndThrowFatalException(
                        "Unexpected error attempting to get the first offset.",
                        ex
                    )
                }
            }
        }
    }

    override fun beginningOffsets(
        partitions: Collection<CordaTopicPartition>,
    ): Map<CordaTopicPartition, Long> {
        return try {
            val partitionMap = consumer.beginningOffsets(partitions.toTopicPartitions(config.topicPrefix))
            partitionMap.mapKeys { it.key.toCordaTopicPartition(config.topicPrefix) }
        } catch (ex: Exception) {
            when (ex) {
                is IllegalStateException,
                is AuthenticationException,
                is AuthorizationException,
                -> {
                    logErrorAndThrowFatalException("Fatal error attempting to get end offsets.", ex)
                }

                is TimeoutException -> {
                    logWarningAndThrowIntermittentException(
                        "Intermittent error attempting to get end offsets.",
                        ex
                    )
                }

                else -> {
                    logErrorAndThrowFatalException("Unexpected error attempting to get end offsets.", ex)
                }
            }
        }
    }


    override fun endOffsets(
        partitions: Collection<CordaTopicPartition>,
    ): Map<CordaTopicPartition, Long> {
        return try {
            val partitionMap = consumer.endOffsets(partitions.toTopicPartitions(config.topicPrefix))
            partitionMap.mapKeys { it.key.toCordaTopicPartition(config.topicPrefix) }
        } catch (ex: Exception) {
            when (ex) {
                is IllegalStateException,
                is AuthenticationException,
                is AuthorizationException,
                -> {
                    logErrorAndThrowFatalException("Fatal error attempting to get end offsets.", ex)
                }

                is TimeoutException -> {
                    logWarningAndThrowIntermittentException(
                        "Intermittent error attempting to get end offsets.",
                        ex
                    )
                }

                else -> {
                    logErrorAndThrowFatalException("Unexpected error attempting to get end offsets.", ex)
                }
            }
        }
    }

    override fun resume(partitions: Collection<CordaTopicPartition>) {
        try {
            consumer.resume(partitions.toTopicPartitions(config.topicPrefix))
        } catch (ex: Exception) {
            when (ex) {
                is IllegalStateException -> {
                    logErrorAndThrowFatalException("Fatal error attempting to resume.", ex)
                }

                else -> {
                    logErrorAndThrowFatalException("Unexpected error attempting to resume.", ex)
                }
            }
        }
    }

    override fun pause(partitions: Collection<CordaTopicPartition>) {
        try {
            consumer.pause(partitions.toTopicPartitions(config.topicPrefix))
        } catch (ex: Exception) {
            when (ex) {
                is IllegalStateException -> {
                    logErrorAndThrowFatalException("Fatal error attempting to pause.", ex)
                }

                else -> {
                    logErrorAndThrowFatalException("Unexpected error attempting to pause.", ex)
                }
            }
        }
    }

    override fun paused(): Set<CordaTopicPartition> {
        return try {
            consumer.paused().toCordaTopicPartitions(config.topicPrefix).toSet()
        } catch (ex: Exception) {
            when (ex) {
                is IllegalStateException -> {
                    logErrorAndThrowFatalException("Fatal error attempting to get paused.", ex)
                }

                is ConcurrentModificationException -> {
                    logWarningAndThrowIntermittentException(
                        "Intermittent error attempting to get paused.",
                        ex
                    )
                }

                else -> {
                    logErrorAndThrowFatalException(
                        "Unexpected error attempting to get paused.",
                        ex
                    )
                }
            }
        }
    }

    internal fun groupMetadata(): ConsumerGroupMetadata {
        return try {
            this.consumer.groupMetadata()
        } catch (ex: Exception) {
            throw CordaMessageAPIFatalException("Could not get groupMetadata", ex)
        }
    }

    override fun setDefaultRebalanceListener(defaultListener: CordaConsumerRebalanceListener) {
        this.defaultListener = defaultListener
    }
}

fun CordaConsumerRebalanceListener.toKafkaListener(): ConsumerRebalanceListener {
    return object : ConsumerRebalanceListener {
        override fun onPartitionsRevoked(partitions: Collection<TopicPartition>) {
            this@toKafkaListener.onPartitionsRevoked(
                partitions.map { CordaTopicPartition(it.topic(), it.partition()) }
            )
        }

        override fun onPartitionsAssigned(partitions: Collection<TopicPartition>) {
            this@toKafkaListener.onPartitionsAssigned(
                partitions.map { CordaTopicPartition(it.topic(), it.partition()) }
            )
        }
    }
}
