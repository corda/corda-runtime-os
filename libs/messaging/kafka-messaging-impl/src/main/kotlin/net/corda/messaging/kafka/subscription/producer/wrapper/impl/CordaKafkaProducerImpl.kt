package net.corda.messaging.kafka.subscription.producer.wrapper.impl

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.records.Record
import net.corda.messaging.kafka.properties.KafkaProperties
import net.corda.messaging.kafka.properties.PublisherConfigProperties
import net.corda.messaging.kafka.subscription.producer.wrapper.CordaKafkaProducer
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.Producer
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
    private val avroSchemaRegistry: AvroSchemaRegistry,
    config: Config,
    private val producer: Producer<Any, Any>,
    private val consumer: Consumer<*, *>,
    ) : CordaKafkaProducer, Producer<Any, Any> by producer {

    private val closeTimeout = config.getLong(KafkaProperties.PRODUCER_CLOSE_TIMEOUT)
    private val topicPrefix = config.getString(KafkaProperties.KAFKA_TOPIC_PREFIX)
    private val clientId =  config.getString(PublisherConfigProperties.PUBLISHER_CLIENT_ID)

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
                    log.error("Error occurred beginning transaction", ex)
                    throw CordaMessageAPIFatalException("Error occurred beginning transaction", ex)
                }
                is KafkaException -> {
                    log.error("Error occurred beginning transaction", ex)
                    throw CordaMessageAPIIntermittentException("Error occurred beginning transaction", ex)
                }
            }
        }
    }

    override fun abortTransaction() {
        try {
            producer.abortTransaction()
        } catch (ex: Exception) {
            log.error("Failed to abort transaction.", ex)
            when (ex) {
                is IllegalStateException,
                is ProducerFencedException,
                is UnsupportedVersionException,
                is AuthorizationException,
                is InvalidProducerEpochException -> {
                    throw CordaMessageAPIFatalException("Failed to abort transaction.", ex)
                }
                is TimeoutException,
                is InterruptException,
                is KafkaException -> {
                    throw CordaMessageAPIIntermittentException("Failed to abort transaction.", ex)
                }
                else -> {
                    throw CordaMessageAPIFatalException("Error occurred aborting transaction", ex)
                }
            }
        }
    }

    override fun tryCommitTransaction() {
        try {
            producer.commitTransaction()
        } catch (ex: Exception) {
            log.error("Error occurred committing transaction", ex)
            producer.abortTransaction()
            when (ex) {
                is IllegalStateException,
                is ProducerFencedException,
                is UnsupportedVersionException,
                is AuthorizationException,
                is InvalidProducerEpochException -> {
                    throw CordaMessageAPIFatalException("Error occurred beginning transaction", ex)
                }
                is TimeoutException,
                is InterruptException,
                is KafkaException -> {
                    throw CordaMessageAPIIntermittentException("Error occurred committing transaction", ex)
                }
                else -> {
                    throw CordaMessageAPIFatalException("Error occurred beginning transaction", ex)
                }
            }
        }
    }

    override fun sendOffsetsToTransaction() {
        try {
            producer.sendOffsetsToTransaction(consumerOffsets(), consumer.groupMetadata())
        } catch (ex: Exception) {
            log.error("Error occurred sending offsets for transaction", ex)
            when (ex) {
                is IllegalStateException,
                is ProducerFencedException,
                is UnsupportedVersionException,
                is UnsupportedForMessageFormatException,
                is AuthorizationException,
                is CommitFailedException,
                is InvalidProducerEpochException,
                is FencedInstanceIdException -> {
                    throw CordaMessageAPIFatalException("Error occurred beginning transaction", ex)
                }
                is TimeoutException,
                is InterruptException,
                is KafkaException -> {
                    throw CordaMessageAPIIntermittentException("Error occurred beginning transaction", ex)
                } else -> {
                    throw CordaMessageAPIFatalException("Error occurred beginning transaction", ex)
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
            log.error("CordaKafkaPublisher failed to close producer safely. ClientId: $clientId", ex)
        }
    }

    /**
     * Generate the consumer offsets.
     */
    private fun consumerOffsets(): Map<TopicPartition, OffsetAndMetadata> {
        val offsets = mutableMapOf<TopicPartition, OffsetAndMetadata>()
        for (topicPartition in consumer.assignment()) {
            offsets[topicPartition] = OffsetAndMetadata(consumer.position(topicPartition), null)
        }
        return offsets
    }
}
