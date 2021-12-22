package net.corda.messagebus.kafka.consumer

import com.typesafe.config.Config
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.CLOSE_TIMEOUT
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.COMMIT_OFFSET_MAX_RETRIES
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.POLL_TIMEOUT
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.SUBSCRIBE_MAX_RETRIES
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.TOPIC_NAME
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.TOPIC_PREFIX
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.v5.base.util.contextLogger
import org.apache.kafka.clients.CommonClientConfigs
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
    config: Config,
    private val consumer: Consumer<K, V>,
    defaultListener: CordaConsumerRebalanceListener? = null,
) : CordaConsumer<K, V> {

    companion object {
        private val log: Logger = contextLogger()
    }

    internal var defaultListener = defaultListener

    private val consumerPollTimeout = Duration.ofMillis(config.getLong(POLL_TIMEOUT))
    private val consumerCloseTimeout = Duration.ofMillis(config.getLong(CLOSE_TIMEOUT))
    private val consumerSubscribeMaxRetries = config.getLong(SUBSCRIBE_MAX_RETRIES)
    private val consumerCommitOffsetMaxRetries = config.getLong(COMMIT_OFFSET_MAX_RETRIES)
    private val topicPrefix = config.getString(TOPIC_PREFIX)
    private val topic = config.getString(TOPIC_NAME)
    private val groupName = config.getString(CommonClientConfigs.GROUP_ID_CONFIG)

    override fun close() {
        try {
            consumer.close(consumerCloseTimeout)
        } catch (ex: Exception) {
            log.error("CordaKafkaConsumer failed to close consumer from group $groupName for topic $topic.", ex)
        }
    }

    override fun close(timeout: Duration) {
        try {
            consumer.close(timeout)
        } catch (ex: Exception) {
            log.error("CordaKafkaConsumer failed to close consumer from group $groupName for topic $topic.", ex)
        }
    }

    override fun poll(): List<CordaConsumerRecord<K, V>> {
        return poll(consumerPollTimeout)

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
                is InvalidOffsetException -> {
                    logErrorAndThrowFatalException("Error attempting to poll from topic $topic", ex)
                }
                is WakeupException,
                is InterruptException,
                is KafkaException -> {
                    logWarningAndThrowIntermittentException("Error attempting to poll from topic $topic", ex)
                }
                else -> logErrorAndThrowFatalException("Unexpected error attempting to poll from topic $topic", ex)
            }
        }

        return consumerRecords.map {
            CordaConsumerRecord(
                it.topic().removePrefix(topicPrefix),
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
        val topicPartition = TopicPartition(topicPrefix + event.topic, event.partition)
        offsets[topicPartition] = OffsetAndMetadata(event.offset + 1, metaData)
        var attempts = 0L
        var attemptCommit = true

        while (attemptCommit) {
            try {
                consumer.commitSync(offsets)
                attemptCommit = false
            } catch (ex: Exception) {
                when (ex) {
                    is InterruptException,
                    is TimeoutException -> {
                        attempts++
                        handleErrorRetry(
                            "Failed to commitSync offsets for record $event on topic $topic",
                            attempts, consumerCommitOffsetMaxRetries, ex
                        )
                    }
                    is CommitFailedException,
                    is AuthenticationException,
                    is AuthorizationException,
                    is IllegalArgumentException,
                    is FencedInstanceIdException -> {
                        logErrorAndThrowFatalException(
                            "Error attempting to commitSync offsets for record $event on topic $topic",
                            ex
                        )
                    }
                    else -> {
                        logErrorAndThrowFatalException(
                            "Unexpected error attempting to commitSync offsets " +
                                    "for record $event on topic $topic", ex
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
            if (!it.contains(topicPrefix)) {
                topicPrefix + it
            } else {
                it
            }
        }
        var attempts = 0L
        var attemptSubscription = true
        while (attemptSubscription) {
            try {
                subscribeToTopics(listener, newTopics)
                attemptSubscription = false
            } catch (ex: Exception) {
                val message = "CordaKafkaConsumer failed to subscribe a consumer from group $groupName to topic $topic"
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
                    is KafkaException -> {
                        attempts++
                        handleErrorRetry(message, attempts, consumerSubscribeMaxRetries, ex)
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


    override fun getPartitions(topic: String, timeout: Duration): List<CordaTopicPartition> {
        val listOfPartitions: List<PartitionInfo> = try {
            consumer.partitionsFor(topicPrefix + topic, timeout)
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
            ?: logWarningAndThrowIntermittentException("Partitions for topic $topic are null. Kafka may not have completed startup.")

        return listOfPartitions.map {
            CordaTopicPartition(it.topic().removePrefix(topicPrefix), it.partition())
        }
    }

    /**
     * Handle retry logic. If max attempts have not been reached log a warning.
     * otherwise throw [CordaMessageAPIFatalException]
     */
    private fun handleErrorRetry(errorMessage: String, currentAttempt: Long, maxRetries: Long, ex: Exception) {
        if (currentAttempt < maxRetries) {
            log.warn("$errorMessage. Retrying.", ex)
        } else {
            logErrorAndThrowFatalException("$errorMessage. Max Retries exceeded.", ex)
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
            val newPartition = partitions.map { TopicPartition(topicPrefix + it.topic, it.partition) }
            consumer.assign(newPartition)
        } catch (ex: Exception) {
            when (ex) {
                is ConcurrentModificationException -> {
                    logWarningAndThrowIntermittentException(
                        "Intermittent error attempting to assign on topic $topic",
                        ex
                    )
                }
                is IllegalArgumentException,
                is IllegalStateException -> {
                    logErrorAndThrowFatalException("Fatal error attempting to assign on topic $topic", ex)
                }
                else -> {
                    logErrorAndThrowFatalException("Unexpected error attempting to resume on topic $topic", ex)
                }
            }
        }
    }

    override fun assignment(): Set<CordaTopicPartition> {
        return try {
            val partitionSet = consumer.assignment()
            partitionSet.map {
                CordaTopicPartition(it.topic().removePrefix(topicPrefix), it.partition())
            }.toSet()
        } catch (ex: Exception) {
            when (ex) {
                is IllegalStateException -> {
                    logErrorAndThrowFatalException("Fatal error attempting to get assignment on topic $topic", ex)
                }
                is ConcurrentModificationException -> {
                    logWarningAndThrowIntermittentException(
                        "Intermittent error attempting to get assignment on topic $topic",
                        ex
                    )
                }
                else -> {
                    logErrorAndThrowFatalException("Unexpected error attempting to get assignment on topic $topic", ex)
                }
            }
        }
    }

    override fun position(partition: CordaTopicPartition): Long {
        return try {
            consumer.position(TopicPartition(topicPrefix + partition.topic, partition.partition))
        } catch (ex: Exception) {
            when (ex) {
                is AuthenticationException,
                is AuthorizationException,
                is InvalidOffsetException,
                is IllegalStateException -> {
                    logErrorAndThrowFatalException("Fatal error attempting to get position on topic $topic", ex)
                }
                is InterruptException,
                is WakeupException,
                is TimeoutException,
                is KafkaException -> {
                    logWarningAndThrowIntermittentException(
                        "Intermittent error attempting to get position on topic $topic",
                        ex
                    )
                }
                else -> {
                    logErrorAndThrowFatalException("Unexpected error attempting to get position on topic $topic", ex)
                }
            }
        }
    }

    override fun seek(partition: CordaTopicPartition, offset: Long) {
        try {
            consumer.seek(TopicPartition(topicPrefix + partition.topic, partition.partition), offset)
        } catch (ex: Exception) {
            when (ex) {
                is IllegalArgumentException,
                is IllegalStateException -> {
                    logErrorAndThrowFatalException("Fatal error attempting to get the first offset on topic $topic", ex)
                }
                is ConcurrentModificationException -> {
                    logWarningAndThrowIntermittentException(
                        "Intermittent error attempting to get the first offset on topic $topic",
                        ex
                    )
                }
                else -> {
                    logErrorAndThrowFatalException(
                        "Unexpected error attempting to get the first offset on topic $topic",
                        ex
                    )
                }
            }
        }
    }

    override fun seekToBeginning(partitions: Collection<CordaTopicPartition>) {
        try {
            val newPartitions = partitions.map { TopicPartition(topicPrefix + it.topic, it.partition) }
            consumer.seekToBeginning(newPartitions)
        } catch (ex: Exception) {
            when (ex) {
                is IllegalArgumentException,
                is IllegalStateException -> {
                    logErrorAndThrowFatalException("Fatal error attempting to get the first offset on topic $topic", ex)
                }
                is ConcurrentModificationException -> {
                    logWarningAndThrowIntermittentException(
                        "Intermittent error attempting to get the first offset on topic $topic",
                        ex
                    )
                }
                else -> {
                    logErrorAndThrowFatalException(
                        "Unexpected error attempting to get the first offset on topic $topic",
                        ex
                    )
                }
            }
        }
    }

    override fun seekToEnd(partitions: Collection<CordaTopicPartition>) {
        try {
            val newPartitions = partitions.map { TopicPartition(topicPrefix + it.topic, it.partition) }
            consumer.seekToEnd(newPartitions)
        } catch (ex: Exception) {
            when (ex) {
                is IllegalArgumentException,
                is IllegalStateException -> {
                    logErrorAndThrowFatalException("Fatal error attempting to get the first offset on topic $topic", ex)
                }
                is ConcurrentModificationException -> {
                    logWarningAndThrowIntermittentException(
                        "Intermittent error attempting to get the first offset on topic $topic",
                        ex
                    )
                }
                else -> {
                    logErrorAndThrowFatalException(
                        "Unexpected error attempting to get the first offset on topic $topic",
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
            val newPartitions = partitions.map { TopicPartition(topicPrefix + it.topic, it.partition) }
            val partitionMap = consumer.beginningOffsets(newPartitions)
            partitionMap.map { (key, value) ->
                CordaTopicPartition(
                    key.topic().removePrefix(topicPrefix),
                    key.partition()
                ) to value
            }.toMap()
        } catch (ex: Exception) {
            when (ex) {
                is IllegalStateException,
                is AuthenticationException,
                is AuthorizationException -> {
                    logErrorAndThrowFatalException("Fatal error attempting to get end offsets on topic $topic", ex)
                }
                is TimeoutException -> {
                    logWarningAndThrowIntermittentException(
                        "Intermittent error attempting to get end offsets on topic $topic",
                        ex
                    )
                }
                else -> {
                    logErrorAndThrowFatalException("Unexpected error attempting to get end offsets on topic $topic", ex)
                }
            }
        }
    }


    override fun endOffsets(
        partitions: Collection<CordaTopicPartition>
    ): Map<CordaTopicPartition, Long> {
        return try {
            val newPartitions = partitions.map { TopicPartition(topicPrefix + it.topic, it.partition) }
            val partitionMap = consumer.endOffsets(newPartitions)
            partitionMap.map { (key, value) ->
                CordaTopicPartition(
                    key.topic().removePrefix(topicPrefix),
                    key.partition()
                ) to value
            }.toMap()
        } catch (ex: Exception) {
            when (ex) {
                is IllegalStateException,
                is AuthenticationException,
                is AuthorizationException -> {
                    logErrorAndThrowFatalException("Fatal error attempting to get end offsets on topic $topic", ex)
                }
                is TimeoutException -> {
                    logWarningAndThrowIntermittentException(
                        "Intermittent error attempting to get end offsets on topic $topic",
                        ex
                    )
                }
                else -> {
                    logErrorAndThrowFatalException("Unexpected error attempting to get end offsets on topic $topic", ex)
                }
            }
        }
    }

    override fun resume(partitions: Collection<CordaTopicPartition>) {
        try {
            val newPartitions = partitions.map { TopicPartition(topicPrefix + it.topic, it.partition) }
            consumer.resume(newPartitions)
        } catch (ex: Exception) {
            when (ex) {
                is IllegalStateException -> {
                    logErrorAndThrowFatalException("Fatal error attempting to resume on topic $topic", ex)
                }
                else -> {
                    logErrorAndThrowFatalException("Unexpected error attempting to resume on topic $topic", ex)
                }
            }
        }
    }

    override fun pause(partitions: Collection<CordaTopicPartition>) {
        try {
            val newPartitions = partitions.map { TopicPartition(topicPrefix + it.topic, it.partition) }
            consumer.pause(newPartitions)
        } catch (ex: Exception) {
            when (ex) {
                is IllegalStateException -> {
                    logErrorAndThrowFatalException("Fatal error attempting to pause on topic $topic", ex)
                }
                else -> {
                    logErrorAndThrowFatalException("Unexpected error attempting to pause on topic $topic", ex)
                }
            }
        }
    }

    override fun paused(): Set<CordaTopicPartition> {
        return try {
            val partitionSet = consumer.paused()
            partitionSet.map {
                CordaTopicPartition(it.topic().removePrefix(topicPrefix), it.partition())
            }.toSet()
        } catch (ex: Exception) {
            when (ex) {
                is IllegalStateException -> {
                    logErrorAndThrowFatalException("Fatal error attempting to get paused on topic $topic", ex)
                }
                is ConcurrentModificationException -> {
                    logWarningAndThrowIntermittentException(
                        "Intermittent error attempting to get paused on topic $topic",
                        ex
                    )
                }
                else -> {
                    logErrorAndThrowFatalException(
                        "Unexpected error attempting to get paused on topic $topic",
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
