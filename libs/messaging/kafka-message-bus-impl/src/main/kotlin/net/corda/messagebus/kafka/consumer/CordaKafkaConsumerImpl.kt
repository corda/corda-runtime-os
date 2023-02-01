package net.corda.messagebus.kafka.consumer

import java.io.ByteArrayOutputStream
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
import net.corda.messaging.api.chunking.ConsumerChunkService
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
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Wrapper for a Kafka Consumer.
 */
@Suppress("TooManyFunctions", "LongParameterList")
class CordaKafkaConsumerImpl<K : Any, V : Any>(
    private val config: ResolvedConsumerConfig,
    private val consumer: Consumer<Any, Any>,
    private var defaultListener: CordaConsumerRebalanceListener? = null,
    private val consumerChunkService: ConsumerChunkService<K, V>,
    private val vClazz: Class<V>,
    private val onSerializationError: (ByteArray) -> Unit,
) : CordaConsumer<K, V> {

    private val bufferedChunksByPartition = mutableMapOf<Int, MutableMap<ChunkKey, Chunk>>()

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
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
        val consumerRecords = try {
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
                is InvalidOffsetException -> {
                    logErrorAndThrowFatalException("Error attempting to poll.", ex)
                }
                is WakeupException,
                is InterruptException,
                is KafkaException -> {
                    logWarningAndThrowIntermittentException("Error attempting to poll.", ex)
                }
                else -> logErrorAndThrowFatalException("Unexpected error attempting to poll.", ex)
            }
        }

        return consumerRecords.mapNotNull {
            parseRecord(it)
        }
    }

    /**
     * Take a record polled and process it.
     * If it is a chunk, then check to see if the chunks can be reassemnbled and returned.
     * If the expected type of the Consumer is of type [Chunk] then do not try to reassemble chunks.
     * If the record is a normal records, verify the consumer is not waiting on more chunks and return the record.
     * @param consumerRecord record to parse
     * @return Complete record safe to return from the consumer, or null if it is a partial chunk
     */
    private fun parseRecord(consumerRecord: ConsumerRecord<Any, Any>): CordaConsumerRecord<K, V>? {
        val partition = consumerRecord.partition()
        val value = consumerRecord.value()
        val key = consumerRecord.key()
        return if (key is ChunkKey && vClazz != Chunk::class && value is Chunk) {
            getRecordIfComplete(key, value, consumerRecord)
        } else {
            verifyNoPartialChunks(partition)
            CordaConsumerRecord(
                consumerRecord.topic().removePrefix(config.topicPrefix),
                consumerRecord.partition(),
                consumerRecord.offset(),
                uncheckedCast(consumerRecord.key()),
                uncheckedCast(consumerRecord.value()),
                consumerRecord.timestamp(),
            )
        }
    }

    /**
     * This shouldn't be possible but if we somehow end up with partial incomplete chunks, then DLQ the chunks.
     * Chunks are always expected to be committed via transactions so normal records should not be interleaved with buffered chunks.
     * @param partition partition to verify no partial chunks interleaved with complete records.
     */
    private fun verifyNoPartialChunks(partition: Int) {
        val chunks = bufferedChunksByPartition[partition]
        if (chunks != null && chunks.isNotEmpty()) {
            val out = ByteArrayOutputStream()
            chunks.values.forEach { out.write(it.data.array()) }
            onSerializationError(out.toByteArray())
            bufferedChunksByPartition.remove(partition)
        }
    }

    /**
     * Take a [chunk] from a [consumerRecord] and add it to the [bufferedChunksByPartition].
     * If the chunk contains a checksum, then the consumerChunkService can be used to reassemble the chunks.
     * Clear the [bufferedChunksByPartition] as no more chunks are expected for this case.
     * If the [consumerChunkService] returns null then the deserialization error will be handled by the [consumerChunkService]
     * @param chunkKey The record key deserialized as a [ChunkKey]
     * @param chunk The record value deserialized as a [Chunk]
     * @param consumerRecord the consumer record for the chunk. Can be used to set properties on the reassembled object.
     * @return The reassembled chunks as a ConsumerRecord. Null if this record is not complete.
     */
    private fun getRecordIfComplete(
        chunkKey: ChunkKey,
        chunk: Chunk,
        consumerRecord: ConsumerRecord<Any, Any>,
    ): CordaConsumerRecord<K, V>? {
        val partition = consumerRecord.partition()
        val chunksRead = addChunk(chunkKey, chunk, partition)
        //We know that the final chunk will have a checksum for message bus level chunking.
        // If it doesn't then this will be caught by [verifyNoPartialChunks]
        return if (chunk.checksum != null) {
            bufferedChunksByPartition.remove(partition)
            //if deserialization fails onError handling executed within consumerChunkService
            consumerChunkService.assembleChunks(chunksRead)?.let {
                CordaConsumerRecord(
                    consumerRecord.topic().removePrefix(config.topicPrefix),
                    partition,
                    consumerRecord.offset(),
                    it.first,
                    it.second,
                    consumerRecord.timestamp(),
                )
            }
        } else {
            log.trace { "Read chunk with part number ${chunk.partNumber} and requestId ${chunk.requestId}" }
            null
        }
    }

    /**
     * Buffer a chunk into the object [bufferedChunksByPartition]
     * @param chunkKey chunk key for the chunk to buffer
     * @param chunk chunk value to buffer
     * @param partition the partition for this chunk
     * @return The partial chunks read so far for this partition
     */
    private fun addChunk(
        chunkKey: ChunkKey,
        chunk: Chunk,
        partition: Int,
    ): MutableMap<ChunkKey, Chunk> {
        return bufferedChunksByPartition.computeIfAbsent(partition) {
            mutableMapOf()
        }.apply {
            this[chunkKey] = chunk
        }
    }

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
                    is TimeoutException -> {
                        logWarningAndThrowIntermittentException("Failed to commitSync offsets for record $event.", ex)
                    }
                    is CommitFailedException,
                    is AuthenticationException,
                    is AuthorizationException,
                    is IllegalArgumentException,
                    is FencedInstanceIdException -> {
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
        topics: Collection<String>
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
                is AuthorizationException -> {
                    logErrorAndThrowFatalException("Fatal error attempting to get partitions on topic $topic", ex)
                }
                is InterruptException,
                is WakeupException,
                is TimeoutException,
                is KafkaException -> {
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
            ?: logWarningAndThrowIntermittentException("Partitions for topic $topic are null. " +
                    "Kafka may not have completed startup.")

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
                is IllegalStateException -> {
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
                is IllegalStateException -> {
                    logErrorAndThrowFatalException("Fatal error attempting to get position.", ex)
                }
                is InterruptException,
                is WakeupException,
                is TimeoutException,
                is KafkaException -> {
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
                is IllegalStateException -> {
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
                is IllegalStateException -> {
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
                is IllegalStateException -> {
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
        partitions: Collection<CordaTopicPartition>
    ): Map<CordaTopicPartition, Long> {
        return try {
            val partitionMap = consumer.beginningOffsets(partitions.toTopicPartitions(config.topicPrefix))
            partitionMap.mapKeys { it.key.toCordaTopicPartition(config.topicPrefix) }
        } catch (ex: Exception) {
            when (ex) {
                is IllegalStateException,
                is AuthenticationException,
                is AuthorizationException -> {
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
        partitions: Collection<CordaTopicPartition>
    ): Map<CordaTopicPartition, Long> {
        return try {
            val partitionMap = consumer.endOffsets(partitions.toTopicPartitions(config.topicPrefix))
            partitionMap.mapKeys { it.key.toCordaTopicPartition(config.topicPrefix) }
        } catch (ex: Exception) {
            when (ex) {
                is IllegalStateException,
                is AuthenticationException,
                is AuthorizationException -> {
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
