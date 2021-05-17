package net.corda.messaging.kafka.subscription.consumer.wrapper.impl

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.properties.KafkaProperties
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_POLL_TIMEOUT
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.internal.uncheckedCast
import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.AuthenticationException
import org.apache.kafka.common.errors.AuthorizationException
import org.apache.kafka.common.errors.FencedInstanceIdException
import org.apache.kafka.common.errors.InterruptException
import org.apache.kafka.common.errors.TimeoutException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Duration

/**
 * Wrapper for a Kafka Consumer.
 */
class CordaKafkaConsumerImpl<K : Any, V : Any> (
    kafkaConfig: Config,
    subscriptionConfig: SubscriptionConfig,
    override val consumer: Consumer<K, ByteBuffer>,
    private val listener: ConsumerRebalanceListener,
    private val avroSchemaRegistry: AvroSchemaRegistry
) : CordaKafkaConsumer<K, V> {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    private val consumerPollTimeout = Duration.ofMillis(kafkaConfig.getLong(CONSUMER_POLL_TIMEOUT))
    private val consumerCloseTimeout = Duration.ofMillis(kafkaConfig.getLong(KafkaProperties.CONSUMER_CLOSE_TIMEOUT))
    private val consumerSubscribeMaxRetries = kafkaConfig.getLong(KafkaProperties.CONSUMER_SUBSCRIBE_MAX_RETRIES)
    private val consumerCommitOffsetMaxRetries = kafkaConfig.getLong(KafkaProperties.CONSUMER_COMMIT_OFFSET_MAX_RETRIES)
    private val groupName = subscriptionConfig.groupName
    private val topicPrefix = kafkaConfig.getString(KafkaProperties.KAFKA_TOPIC_PREFIX)
    private val topic = subscriptionConfig.eventTopic

    @Suppress("TooGenericExceptionCaught")
    override fun close() {
        try {
            consumer.close(consumerCloseTimeout)
        } catch (ex: Exception) {
            log.error("CordaKafkaConsumer failed to close consumer from group $groupName for topic $topic.", ex)
        }
    }

    override fun poll(): List<ConsumerRecord<K, ByteBuffer>> {
        val consumerRecords = consumer.poll(consumerPollTimeout)
        return consumerRecords.sortedBy { it.timestamp() }
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

    override fun getRecord(consumerRecord: ConsumerRecord<K, ByteBuffer>) : Record<K, V> {
        return try {
            val classType = avroSchemaRegistry.getClassType(consumerRecord.value())
            val value: V = uncheckedCast(avroSchemaRegistry.deserialize(consumerRecord.value(), classType, null))
            val topic = consumerRecord.topic().substringAfter(topicPrefix)
            Record(topic, consumerRecord.key(), value)
        } catch (ex: CordaRuntimeException) {
            val message = "CordaKafkaConsumer failed to deserialize record with key ${consumerRecord.key()}. Group $groupName,topic $topic."
            log.error(message, ex)
            throw CordaMessageAPIFatalException(message, ex)
        }
    }

    override fun commitSyncOffsets(event: ConsumerRecord<K, ByteBuffer>, metaData: String?) {
        val offsets = mutableMapOf<TopicPartition, OffsetAndMetadata>()
        val topicPartition = TopicPartition(event.topic(), event.partition())
        offsets[topicPartition] = OffsetAndMetadata(event.offset() + 1, metaData)
        var attempts = 0L
        var attemptCommit = true

        while (attemptCommit) {
            try {
                consumer.commitSync(offsets);
                attemptCommit = false
            }
            catch (ex: InterruptException) {
                attempts++
                handleErrorRetry("Failed to commitSync offsets for record $event on topic $topic", attempts, consumerCommitOffsetMaxRetries, ex)
            } catch (ex: TimeoutException) {
                attempts++
                handleErrorRetry("Failed to commitSync offsets for record $event on topic $topic", attempts, consumerCommitOffsetMaxRetries, ex)
            } catch (ex: CommitFailedException) {
                logErrorAndThrowFatalException("Error attempting to commitSync offsets for record $event on topic $topic.", ex)
            } catch (ex: AuthenticationException) {
                logErrorAndThrowFatalException("Error attempting to commitSync offsets for record $event on topic $topic.", ex)
            } catch (ex: AuthorizationException) {
                logErrorAndThrowFatalException("Error attempting to commitSync offsets for record $event on topic $topic.", ex)
            } catch (ex: IllegalArgumentException) {
                logErrorAndThrowFatalException("Error attempting to commitSync offsets for record $event on topic $topic.", ex)
            } catch (ex: FencedInstanceIdException) {
                logErrorAndThrowFatalException("Error attempting to commitSync offsets for record $event on topic $topic.", ex)
            }
        }
    }

    override fun subscribeToTopic() {
        var attempts = 0L
        var attemptSubscription = true
        while (attemptSubscription) {
            try {
                consumer.subscribe(listOf(topicPrefix + topic), listener)
                attemptSubscription = false
            } catch (ex: IllegalStateException) {
                logErrorAndThrowFatalException("CordaKafkaConsumer failed to subscribe a consumer from group $groupName to topic $topic. " +
                        "Consumer is already subscribed to this topic. Closing subscription.", ex)
            } catch (ex: IllegalArgumentException) {
                logErrorAndThrowFatalException("CordaKafkaConsumer failed to subscribe a consumer from group $groupName to topic $topic. " +
                        "Illegal args provided. Closing subscription.", ex)
            } catch (ex: KafkaException) {
                attempts++
                handleErrorRetry("CordaKafkaConsumer failed to subscribe a consumer from group $groupName to topic $topic. ", attempts, consumerSubscribeMaxRetries, ex)
            }
        }
    }

    /**
     * Handle retry logic. If max attempts have not been reached log a warning.
     * otherwise throw [CordaMessageAPIFatalException]
     */
    private fun handleErrorRetry(errorMessage: String, currentAttempt: Long, maxRetries: Long, ex: Exception) {
        if (currentAttempt < maxRetries) {
            log.warn("$errorMessage Retrying.", ex)
        } else {
            logErrorAndThrowFatalException("$errorMessage Max Retries exceeded.", ex)
        }
    }

    /**
     * Log error and throw [CordaMessageAPIFatalException]
     */
    private fun logErrorAndThrowFatalException(errorMessage: String, ex: Exception) {
        log.error(errorMessage, ex)
        throw CordaMessageAPIFatalException(errorMessage, ex)
    }
}
