package net.corda.messaging.kafka.subscription.consumer.wrapper.impl

import com.typesafe.config.Config
import net.corda.libs.configuration.schema.messaging.TOPIC_PREFIX_PATH
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.CLOSE_TIMEOUT
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.COMMIT_OFFSET_MAX_RETRIES
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.POLL_TIMEOUT
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.SUBSCRIBE_MAX_RETRIES
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.TOPIC_NAME
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.v5.base.util.contextLogger
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.InvalidOffsetException
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.PartitionInfo
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.AuthenticationException
import org.apache.kafka.common.errors.AuthorizationException
import org.apache.kafka.common.errors.FencedInstanceIdException
import org.apache.kafka.common.errors.InterruptException
import org.apache.kafka.common.errors.InvalidGroupIdException
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
    private val defaultListener: ConsumerRebalanceListener?,
) : CordaKafkaConsumer<K, V> {

    companion object {
        private val log: Logger = contextLogger()
    }

    private val consumerPollTimeout = Duration.ofMillis(config.getLong(POLL_TIMEOUT))
    private val consumerCloseTimeout = Duration.ofMillis(config.getLong(CLOSE_TIMEOUT))
    private val consumerSubscribeMaxRetries = config.getLong(SUBSCRIBE_MAX_RETRIES)
    private val consumerCommitOffsetMaxRetries = config.getLong(COMMIT_OFFSET_MAX_RETRIES)
    private val topicPrefix = config.getString(TOPIC_PREFIX_PATH)
    private val topic = config.getString(TOPIC_NAME)
    private val topicWithPrefix = "$topicPrefix$topic"
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

    override fun poll(): List<ConsumerRecord<K, V>> {
        return poll(consumerPollTimeout)

    }

    @Suppress("TooGenericExceptionCaught")
    override fun poll(timeout: Duration): List<ConsumerRecord<K, V>> {
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
            ConsumerRecord(
                it.topic().removePrefix(topicPrefix),
                it.partition(),
                it.offset(),
                it.key(),
                it.value()
            )
        }.sortedBy { it.timestamp() }
    }

    override fun resetToLastCommittedPositions(offsetStrategy: OffsetResetStrategy) {
        val committed = consumer.committed(consumer.assignment())
        for (assignment in consumer.assignment()) {
            val offsetAndMetadata = committed[assignment]
            when {
                offsetAndMetadata != null -> {
                    consumer.seek(assignment, offsetAndMetadata.offset())
                }
                offsetStrategy == OffsetResetStrategy.LATEST -> {
                    consumer.seekToEnd(setOf(assignment))
                }
                else -> {
                    consumer.seekToBeginning(setOf(assignment))
                }
            }
        }
    }

    override fun commitSyncOffsets(event: ConsumerRecord<K, V>, metaData: String?) {
        val offsets = mutableMapOf<TopicPartition, OffsetAndMetadata>()
        val topicPartition = TopicPartition(topicPrefix + event.topic(), event.partition())
        offsets[topicPartition] = OffsetAndMetadata(event.offset() + 1, metaData)
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

    override fun subscribeToTopic(listener: ConsumerRebalanceListener?) =
        subscribe(listOf(topicWithPrefix), listener ?: defaultListener)

    override fun subscribe(topics: Collection<String>, listener: ConsumerRebalanceListener?) {
        val newTopics = topics.map {
            if(!it.contains(topicPrefix)) {
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
        listener: ConsumerRebalanceListener?,
        topics: Collection<String>
    ) {
        when {
            listener != null -> {
                consumer.subscribe(topics, listener)
            }
            defaultListener != null -> {
                consumer.subscribe(topics, defaultListener)
            }
            else -> {
                consumer.subscribe(topics)
            }
        }
    }


    override fun getPartitions(topic: String, duration: Duration): List<TopicPartition> {
        val listOfPartitions: List<PartitionInfo> = try {
            consumer.partitionsFor(topicPrefix + topic, duration)
        } catch (ex: Exception) {
            when (ex) {
                is AuthenticationException,
                is AuthorizationException -> {
                    logErrorAndThrowFatalException("Fatal error attempting to get partitions on topic $topic", ex)
                }
                is InterruptException,
                is WakeupException,
                is KafkaException,
                is TimeoutException -> {
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
            TopicPartition(it.topic().removePrefix(topicPrefix), it.partition())
        }
    }

    override fun assignPartitionsManually(partitions: Set<Int>) {
        val topicPartitions = partitions.map { TopicPartition(topicWithPrefix, it) }
        consumer.assign(topicPartitions)
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

    override fun assign(partitions: Collection<TopicPartition>) {
        try {
            val newPartition = partitions.map { TopicPartition(topicPrefix + it.topic(), it.partition()) }
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

    override fun assignment(): Set<TopicPartition> {
        return try {
            val partitionSet = consumer.assignment()
            partitionSet.map { TopicPartition(it.topic().removePrefix(topicPrefix), it.partition()) }.toSet()
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

    override fun position(partition: TopicPartition): Long {
        return try {
            consumer.position(TopicPartition(topicPrefix + partition.topic(), partition.partition()))
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
                is KafkaException,
                is TimeoutException -> {
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

    override fun seek(partition: TopicPartition, offset: Long) {
        try {
            consumer.seek(TopicPartition(topicPrefix + partition.topic(), partition.partition()), offset)
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

    override fun seekToBeginning(partitions: Collection<TopicPartition>) {
        try {
            val newPartitions = partitions.map { TopicPartition(topicPrefix + it.topic(), it.partition()) }
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

    override fun beginningOffsets(partitions: Collection<TopicPartition>): Map<TopicPartition, Long> {
        return try {
            val newPartitions = partitions.map { TopicPartition(topicPrefix + it.topic(), it.partition()) }
            val partitionMap = consumer.beginningOffsets(newPartitions)
            partitionMap.map { (key, value) ->
                TopicPartition(
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


    override fun endOffsets(partitions: Collection<TopicPartition>): Map<TopicPartition, Long> {
        return try {
            val newPartitions = partitions.map { TopicPartition(topicPrefix + it.topic(), it.partition()) }
            val partitionMap = consumer.endOffsets(newPartitions)
            partitionMap.map { (key, value) ->
                TopicPartition(
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

    override fun resume(partitions: Collection<TopicPartition>) {
        try {
            val newPartitions = partitions.map { TopicPartition(topicPrefix + it.topic(), it.partition()) }
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

    override fun pause(partitions: Collection<TopicPartition>) {
        try {
            val newPartitions = partitions.map { TopicPartition(topicPrefix + it.topic(), it.partition()) }
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

    override fun paused(): Set<TopicPartition> {
        return try {
            val partitionSet = consumer.paused()
            partitionSet.map { TopicPartition(it.topic().removePrefix(topicPrefix), it.partition()) }.toSet()
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

    override fun groupMetadata(): ConsumerGroupMetadata {
        return try {
            consumer.groupMetadata()
        } catch (ex: Exception) {
            when (ex) {
                is IllegalStateException,
                is InvalidGroupIdException -> {
                    logErrorAndThrowFatalException("Fatal error attempting to get group metadata on topic $topic", ex)
                }
                is ConcurrentModificationException -> {
                    logWarningAndThrowIntermittentException(
                        "Intermittent error attempting to get group metadata on topic $topic",
                        ex
                    )
                }
                else -> {
                    logErrorAndThrowFatalException(
                        "Unexpected error attempting to get group metadata on topic $topic",
                        ex
                    )
                }
            }
        }
    }
}
