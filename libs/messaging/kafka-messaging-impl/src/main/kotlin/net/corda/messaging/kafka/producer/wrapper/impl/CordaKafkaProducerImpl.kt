package net.corda.messaging.kafka.producer.wrapper.impl

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.records.Record
import net.corda.messaging.kafka.getStringOrNull
import net.corda.messaging.kafka.producer.wrapper.CordaKafkaProducer
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CLOSE_TIMEOUT
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.TOPIC_PREFIX
import net.corda.v5.base.util.contextLogger
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.AuthorizationException
import org.apache.kafka.common.errors.FencedInstanceIdException
import org.apache.kafka.common.errors.InterruptException
import org.apache.kafka.common.errors.InvalidProducerEpochException
import org.apache.kafka.common.errors.ProducerFencedException
import org.apache.kafka.common.errors.TimeoutException
import org.apache.kafka.common.errors.UnsupportedForMessageFormatException
import org.apache.kafka.common.errors.UnsupportedVersionException
import org.slf4j.Logger
import java.time.Duration

/**
 * Wrapper for the CordaKafkaProducer.
 * Delegate actions to kafka [producer].
 * Wrap calls to [producer] with error handling.
 */
@Suppress("TooGenericExceptionCaught")
class CordaKafkaProducerImpl(
    config: Config,
    private val producer: Producer<Any, Any>
) : CordaKafkaProducer, Producer<Any, Any> by producer {
    private val closeTimeout = config.getLong(CLOSE_TIMEOUT)
    private val topicPrefix = config.getString(TOPIC_PREFIX)
    private val clientId = config.getString(CommonClientConfigs.CLIENT_ID_CONFIG)
    private val transactionalId = config.getStringOrNull(ProducerConfig.TRANSACTIONAL_ID_CONFIG)

    init {
        if (transactionalId != null) {
            initTransactionForProducer()
        }
    }

    private companion object {
        private val log: Logger = contextLogger()
    }

    override fun sendRecords(records: List<Record<*, *>>) {
        for (record in records) {
            producer.send(ProducerRecord(topicPrefix + record.topic, record.key, record.value))
        }
    }

    override fun beginTransaction() {
        try {
            producer.beginTransaction()
        } catch (ex: Exception) {
            when (ex) {
                is IllegalStateException,
                is ProducerFencedException,
                is InvalidProducerEpochException,
                is UnsupportedVersionException,
                is AuthorizationException -> {
                    throw CordaMessageAPIFatalException(
                        "Fatal error occurred beginning transaction " +
                                "for CordaKafkaProducer with clientId $clientId", ex
                    )
                }
                is KafkaException -> {
                    throw CordaMessageAPIIntermittentException(
                        "Error occurred beginning transaction " +
                                "for CordaKafkaProducer with clientId $clientId", ex
                    )
                }
                else -> {
                    throw CordaMessageAPIFatalException(
                        "Unexpected fatal error occurred beginning transaction " +
                                "for CordaKafkaProducer with clientId $clientId", ex
                    )
                }
            }
        }
    }

    override fun abortTransaction() {
        try {
            producer.abortTransaction()
        } catch (ex: Exception) {
            when (ex) {
                is IllegalStateException,
                is ProducerFencedException,
                is UnsupportedVersionException,
                is AuthorizationException,
                is InvalidProducerEpochException -> {
                    throw CordaMessageAPIFatalException(
                        "Fatal error occurred aborting transaction " +
                                "for CordaKafkaProducer with clientId $clientId", ex
                    )
                }
                is TimeoutException,
                is InterruptException,
                is KafkaException -> {
                    throw CordaMessageAPIIntermittentException(
                        "Error occurred aborting transaction " +
                                "for CordaKafkaProducer with clientId $clientId", ex
                    )
                }
                else -> {
                    throw CordaMessageAPIFatalException(
                        "Unexpected error occurred aborting transaction " +
                                "for CordaKafkaProducer with clientId $clientId", ex
                    )
                }
            }
        }
    }

    override fun tryCommitTransaction() {
        try {
            producer.commitTransaction()
        } catch (ex: Exception) {
            producer.abortTransaction()
            when (ex) {
                is IllegalStateException,
                is ProducerFencedException,
                is UnsupportedVersionException,
                is AuthorizationException,
                is InvalidProducerEpochException -> {
                    throw CordaMessageAPIFatalException(
                        "Fatal error occurred committing transaction " +
                                "for CordaKafkaProducer with clientId $clientId", ex
                    )
                }
                is TimeoutException,
                is InterruptException,
                is KafkaException -> {
                    throw CordaMessageAPIIntermittentException(
                        "Error occurred committing transaction " +
                                "for CordaKafkaProducer with clientId $clientId", ex
                    )
                }
                else -> {
                    throw CordaMessageAPIFatalException(
                        "Unexpected error occurred committing transaction " +
                                "for CordaKafkaProducer with clientId $clientId", ex
                    )
                }
            }
        }
    }

    override fun sendOffsetsToTransaction(consumer: Consumer<*, *>) {
        try {
            producer.sendOffsetsToTransaction(consumerOffsets(consumer), consumer.groupMetadata())
        } catch (ex: Exception) {
            when (ex) {
                is IllegalStateException,
                is ProducerFencedException,
                is UnsupportedVersionException,
                is UnsupportedForMessageFormatException,
                is AuthorizationException,
                is CommitFailedException,
                is InvalidProducerEpochException,
                is FencedInstanceIdException -> {
                    throw CordaMessageAPIFatalException(
                        "Error occurred sending offsets for transaction " +
                                "for CordaKafkaProducer with clientId $clientId", ex
                    )
                }
                is TimeoutException,
                is InterruptException,
                is KafkaException -> {
                    throw CordaMessageAPIIntermittentException(
                        "Fatal error occurred sending offsets for transaction " +
                                "for CordaKafkaProducer with clientId $clientId", ex
                    )
                }
                else -> {
                    throw CordaMessageAPIFatalException(
                        "Unexpected error occurred sending offsets for transaction " +
                                "for CordaKafkaProducer with clientId $clientId", ex
                    )
                }
            }
        }
    }

    /**
     * Safely close a producer. If an exception is thrown swallow the error to avoid double exceptions
     */
    @Suppress("TooGenericExceptionCaught")
    override fun close() {
        try {
            producer.close(Duration.ofMillis(closeTimeout))
        } catch (ex: Exception) {
            log.error("CordaKafkaProducer failed to close producer safely. ClientId: $clientId", ex)
        }
    }

    /**
     * Generate the consumer offsets.
     */
    private fun consumerOffsets(consumer: Consumer<*, *>): Map<TopicPartition, OffsetAndMetadata> {
        val offsets = mutableMapOf<TopicPartition, OffsetAndMetadata>()
        for (topicPartition in consumer.assignment()) {
            offsets[topicPartition] = OffsetAndMetadata(consumer.position(topicPartition), null)
        }
        return offsets
    }

    /**
     * Initialise transactions for the [producer].
     * @throws CordaMessageAPIFatalException fatal error occurred.
     * @throws CordaMessageAPIIntermittentException error occurred that can be retried.
     */
    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    private fun initTransactionForProducer() {
        try {
            producer.initTransactions()
        } catch (ex: Exception) {
            val message = "Failed to initialize producer with " +
                    "transactionId $transactionalId for transactions"
            when (ex) {
                is IllegalStateException,
                is UnsupportedVersionException,
                is AuthorizationException -> {
                    throw CordaMessageAPIFatalException(message, ex)
                }
                is KafkaException,
                is InterruptException,
                is TimeoutException -> {
                    throw CordaMessageAPIIntermittentException(message, ex)
                }
                else -> {
                    throw CordaMessageAPIFatalException("$message. Unexpected error occurred.", ex)
                }
            }
        }
    }
}
