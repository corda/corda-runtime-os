package net.corda.messaging.kafka.subscription.consumer.wrapper.impl

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CLOSE_TIMEOUT
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.COMMIT_OFFSET_MAX_RETRIES
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.POLL_TIMEOUT
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.SUBSCRIBE_MAX_RETRIES
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.TOPIC_NAME
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.TOPIC_PREFIX
import net.corda.messaging.kafka.subscription.consumer.wrapper.ConsumerRecordAndMeta
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.v5.base.util.contextLogger
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.clients.consumer.InvalidOffsetException
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.PartitionInfo
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.*
import org.slf4j.Logger
import java.time.Duration

/**
 * Wrapper for a Kafka Consumer.
 */
class CordaKafkaConsumerImpl<K : Any, V : Any>(
    config: Config,
    private val consumer: Consumer<K, V>,
    private val listener: ConsumerRebalanceListener?,
) : CordaKafkaConsumer<K, V>, Consumer<K, V> by consumer {

    companion object {
        private val log: Logger = contextLogger()
    }

    private val consumerPollTimeout = Duration.ofMillis(config.getLong(POLL_TIMEOUT))
    private val consumerCloseTimeout = Duration.ofMillis(config.getLong(CLOSE_TIMEOUT))
    private val consumerSubscribeMaxRetries = config.getLong(SUBSCRIBE_MAX_RETRIES)
    private val consumerCommitOffsetMaxRetries = config.getLong(COMMIT_OFFSET_MAX_RETRIES)
    private val topicPrefix = config.getString(TOPIC_PREFIX)
    private val topic = config.getString(TOPIC_NAME)
    private val groupName = config.getString(CommonClientConfigs.GROUP_ID_CONFIG)

    @Suppress("TooGenericExceptionCaught")
    override fun close() {
        try {
            consumer.close(consumerCloseTimeout)
        } catch (ex: Exception) {
            log.error("CordaKafkaConsumer failed to close consumer from group $groupName for topic $topic.", ex)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun poll(): List<ConsumerRecordAndMeta<K, V>> {
        val consumerRecords = try {
            consumer.poll(consumerPollTimeout)
        } catch (ex: Exception) {
            when (ex) {
                is AuthorizationException,
                is AuthenticationException,
                is IllegalArgumentException,
                is IllegalStateException,
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
        return consumerRecords
            .sortedBy { it.timestamp() }
            .map { ConsumerRecordAndMeta(topicPrefix, it) }
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

    @Suppress("TooGenericExceptionCaught")
    override fun commitSyncOffsets(event: ConsumerRecord<K, V>, metaData: String?) {
        val offsets = mutableMapOf<TopicPartition, OffsetAndMetadata>()
        val topicPartition = TopicPartition(event.topic(), event.partition())
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
                        logErrorAndThrowFatalException("Error attempting to commitSync offsets for record $event on topic $topic", ex)
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

    @Suppress("TooGenericExceptionCaught")
    override fun subscribeToTopic() {
        var attempts = 0L
        var attemptSubscription = true
        while (attemptSubscription) {
            try {
                consumer.subscribe(listOf(topicPrefix + topic), listener)
                attemptSubscription = false
            } catch (ex: Exception) {
                val message = "CordaKafkaConsumer failed to subscribe a consumer from group $groupName to topic $topic"
                when (ex) {
                    is IllegalStateException -> {
                        logErrorAndThrowFatalException("$message. Consumer is already subscribed to this topic. Closing subscription.", ex)
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

    @Suppress("TooGenericExceptionCaught")
    override fun getPartitions(topic: String, duration: Duration): List<TopicPartition> {
        val listOfPartitions: List<PartitionInfo> = try {
            consumer.partitionsFor(topic, duration)
        } catch (ex: Exception) {
            when (ex) {
                is InterruptException,
                is WakeupException,
                is KafkaException,
                is TimeoutException -> {
                    logWarningAndThrowIntermittentException("Intermittent error attempting to get partitions on topic $topic", ex)
                }
                is AuthenticationException,
                is AuthorizationException -> {
                    logErrorAndThrowFatalException("Fatal error attempting to get partitions on topic $topic", ex)
                }
                else -> {
                    logErrorAndThrowFatalException("Unexpected error attempting to get partitions on topic $topic", ex)
                }
            }
        } ?: logWarningAndThrowIntermittentException("Partitions for topic $topic are null. Kafka may not have completed startup.")

        return listOfPartitions.map { partitionInfo ->
            TopicPartition(partitionInfo.topic(), partitionInfo.partition())
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
        if (ex == null) {
            throw CordaMessageAPIIntermittentException(errorMessage)
        } else {
            throw CordaMessageAPIIntermittentException(errorMessage, ex)
        }
    }
}
