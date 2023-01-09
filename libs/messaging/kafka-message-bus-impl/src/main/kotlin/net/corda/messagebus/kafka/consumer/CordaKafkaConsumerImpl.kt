package net.corda.messagebus.kafka.consumer

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
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.v5.base.util.contextLogger
import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
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
import java.time.Duration

/**
 * Wrapper for a Kafka Consumer.
 */
@Suppress("TooManyFunctions")
class CordaKafkaConsumerImpl<K : Any, V : Any>(
    private val config: ResolvedConsumerConfig,
    private val consumer: Consumer<K, V>,
    private var defaultListener: CordaConsumerRebalanceListener? = null,
) : CordaConsumer<K, V> {

    companion object {
        private val log: Logger = contextLogger()
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

        return consumerRecords.map {
            CordaConsumerRecord(
                it.topic().removePrefix(config.topicPrefix),
                it.partition(),
                it.offset(),
                it.key(),
                it.value(),
                it.timestamp(),
            )
        }.sortedBy { it.timestamp }
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
