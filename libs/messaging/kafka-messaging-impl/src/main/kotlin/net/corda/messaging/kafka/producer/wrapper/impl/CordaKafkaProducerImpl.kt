package net.corda.messaging.kafka.producer.wrapper.impl

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.records.Record
import net.corda.messaging.kafka.producer.wrapper.CordaKafkaProducer
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.CLOSE_TIMEOUT
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.TOPIC_PREFIX
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.utils.getRecordListOffsets
import net.corda.messaging.kafka.utils.getStringOrNull
import net.corda.v5.base.util.contextLogger
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
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
import java.util.concurrent.Future

/**
 * Wrapper for the CordaKafkaProducer.
 * Delegate actions to kafka [producer].
 * Wrap calls to [producer] with error handling.
 */
class CordaKafkaProducerImpl(
    config: Config,
    private val producer: Producer<Any, Any>
) : CordaKafkaProducer {
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

    override fun send(record: ProducerRecord<Any, Any>, callback: Callback?): Future<RecordMetadata> {
        val prefixedRecord =
            ProducerRecord(topicPrefix + record.topic(), record.partition(), record.key(), record.value())
        return producer.send(prefixedRecord, callback)
    }

    override fun sendRecords(records: List<Record<*, *>>) {
        for (record in records) {
            producer.send(ProducerRecord(topicPrefix + record.topic, record.key, record.value))
        }
    }

    override fun sendRecordsToPartitions(recordsWithPartitions: List<Pair<Int, Record<*, *>>>) {
        for ((partition, record) in recordsWithPartitions) {
            producer.send(ProducerRecord(topicPrefix + record.topic, partition, record.key, record.value))
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

    override fun commitTransaction() {
        try {
            producer.commitTransaction()
        } catch (ex: Exception) {
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
                    abortTransaction()
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

    override fun sendAllOffsetsToTransaction(consumer: CordaKafkaConsumer<*, *>) {
        trySendOffsetsToTransaction(consumer, null)
    }

    override fun sendRecordOffsetsToTransaction(
        consumer: CordaKafkaConsumer<*, *>,
        records: List<ConsumerRecord<*, *>>
    ) {
        trySendOffsetsToTransaction(consumer, records)
    }

    @Suppress("ThrowsCount")
    private fun trySendOffsetsToTransaction(
        consumer: CordaKafkaConsumer<*, *>,
        records: List<ConsumerRecord<*, *>>? = null
    ) {
        try {
            producer.sendOffsetsToTransaction(consumerOffsets(consumer, records), consumer.groupMetadata())
        } catch (ex: Exception) {
            when (ex) {
                is IllegalStateException,
                is ProducerFencedException,
                is UnsupportedVersionException,
                is UnsupportedForMessageFormatException,
                is AuthorizationException,
                is InvalidProducerEpochException,
                is FencedInstanceIdException -> {
                    throw CordaMessageAPIFatalException(
                        "FatalError occurred sending offset for transaction " +
                                "for CordaKafkaProducer with clientId $clientId", ex
                    )
                }
                is TimeoutException,
                is InterruptException,
                //Failure to commit here might be due to consumer kicked from group. return as intermittent to trigger retry
                is CommitFailedException,
                is KafkaException -> {
                    abortTransaction()
                    throw CordaMessageAPIIntermittentException(
                        "Error occurred sending offset for transaction " +
                                "for CordaKafkaProducer with clientId $clientId", ex
                    )
                }
                else -> {
                    throw CordaMessageAPIFatalException(
                        "Unexpected error occurred record offset for transaction " +
                                "for CordaKafkaProducer with clientId $clientId", ex
                    )
                }
            }
        }
    }

    /**
     * Safely close a producer. If an exception is thrown swallow the error to avoid double exceptions
     */
    override fun close() {
        try {
            producer.close(Duration.ofMillis(closeTimeout))
        } catch (ex: Exception) {
            log.error("CordaKafkaProducer failed to close producer safely. ClientId: $clientId", ex)
        }
    }

    override fun close(timeout: Duration) {
        try {
            producer.close(timeout)
        } catch (ex: Exception) {
            log.error("CordaKafkaProducer failed to close producer safely. ClientId: $clientId", ex)
        }
    }


    /**
     * Generate the consumer offsets.
     */
    private fun consumerOffsets(
        consumer: CordaKafkaConsumer<*, *>,
        records: List<ConsumerRecord<*, *>>? = null
    ): Map<TopicPartition, OffsetAndMetadata> {
        return if (records == null) {
            getConsumerOffsets(consumer)
        } else {
            getRecordListOffsets(records, topicPrefix)
        }
    }

    /**
     * Generate the consumer offsets for poll position in each consumer partition
     */
    private fun getConsumerOffsets(consumer: CordaKafkaConsumer<*, *>): Map<TopicPartition, OffsetAndMetadata> {
        val offsets = mutableMapOf<TopicPartition, OffsetAndMetadata>()
        for (topicPartition in consumer.assignment()) {
            val prefixedTopicPartition =
                TopicPartition(topicPrefix + topicPartition.topic(), topicPartition.partition())
            offsets[prefixedTopicPartition] = OffsetAndMetadata(consumer.position(topicPartition))
        }
        return offsets
    }

    /**
     * Initialise transactions for the [producer].
     * @throws CordaMessageAPIFatalException fatal error occurred.
     * @throws CordaMessageAPIIntermittentException error occurred that can be retried.
     */
    @Suppress("ThrowsCount")
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
