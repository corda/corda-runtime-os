package net.corda.messagebus.kafka.producer

import com.typesafe.config.Config
import net.corda.libs.configuration.schema.messaging.TOPIC_PREFIX_PATH
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.CLOSE_TIMEOUT
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messagebus.getStringOrNull
import net.corda.messagebus.kafka.consumer.CordaKafkaConsumerImpl
import net.corda.messagebus.kafka.utils.toKafkaRecords
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.v5.base.util.contextLogger
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.Callback
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
@Suppress("TooManyFunctions")
class CordaKafkaProducerImpl(
    config: Config,
    private val producer: Producer<Any, Any>
) : CordaProducer {
    private val closeTimeout = config.getLong(CLOSE_TIMEOUT)
    private val topicPrefix = config.getString(TOPIC_PREFIX_PATH)
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

    private fun CordaProducer.Callback.toKafkaCallback(): Callback {
        return Callback { _, ex -> this@toKafkaCallback.onCompletion(ex) }
    }

    override fun send(record: CordaProducerRecord<*, *>, callback: CordaProducer.Callback?) {
        val prefixedRecord =
            ProducerRecord(topicPrefix + record.topic, record.key, record.value)
        producer.send(prefixedRecord, callback?.toKafkaCallback())
    }

    override fun send(record: CordaProducerRecord<*, *>, partition: Int, callback: CordaProducer.Callback?) {
        val prefixedRecord =
            ProducerRecord(topicPrefix + record.topic, partition, record.key, record.value)
        producer.send(prefixedRecord, callback?.toKafkaCallback())
    }

    override fun sendRecords(records: List<CordaProducerRecord<*, *>>) {
        for (record in records) {
            producer.send(ProducerRecord(topicPrefix + record.topic, record.key, record.value))
        }
    }

    override fun sendRecordsToPartitions(recordsWithPartitions: List<Pair<Int, CordaProducerRecord<*, *>>>) {
        for ((partition, record) in recordsWithPartitions) {
            producer.send(ProducerRecord(topicPrefix + record.topic, partition, record.key, record.value))
        }
    }

    override fun beginTransaction() {
        try {
            producer.beginTransaction()
        } catch (ex: Exception) {
            handleException(ex, "beginning transaction")
        }
    }

    override fun abortTransaction() {
        try {
            producer.abortTransaction()
        } catch (ex: Exception) {
            handleException(ex, "aborting transaction")
        }
    }

    override fun commitTransaction() {
        try {
            producer.commitTransaction()
        } catch (ex: Exception) {
            handleException(ex, "committing transaction", true)
        }
    }

    override fun sendAllOffsetsToTransaction(consumer: CordaConsumer<*, *>) {
        trySendOffsetsToTransaction(consumer, null)
    }

    override fun sendRecordOffsetsToTransaction(
        consumer: CordaConsumer<*, *>,
        records: List<CordaConsumerRecord<*, *>>
    ) {
        trySendOffsetsToTransaction(consumer, records.toKafkaRecords())
    }

    @Suppress("ThrowsCount")
    private fun trySendOffsetsToTransaction(
        consumer: CordaConsumer<*, *>,
        records: List<ConsumerRecord<*, *>>? = null
    ) {
        try {
            producer.sendOffsetsToTransaction(
                consumerOffsets(consumer, records),
                (consumer as CordaKafkaConsumerImpl).groupMetadata()
            )
        } catch (ex: Exception) {
            handleException(ex, "sending offset for transaction", true)
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
        consumer: CordaConsumer<*, *>,
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
    private fun getConsumerOffsets(consumer: CordaConsumer<*, *>): Map<TopicPartition, OffsetAndMetadata> {
        val offsets = mutableMapOf<TopicPartition, OffsetAndMetadata>()
        for (topicPartition in consumer.assignment()) {
            val prefixedTopicPartition =
                TopicPartition(topicPrefix + topicPartition.topic, topicPartition.partition)
            offsets[prefixedTopicPartition] =
                OffsetAndMetadata(consumer.position(topicPartition))
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
            val message = "initializing producer with transactionId $transactionalId for transactions"
            handleException(ex, message)
        }
    }

    @Suppress("ThrowsCount")
    private fun handleException(ex: Exception, operation: String, canAbort: Boolean = false) {
        val errorString = "$operation for CordaKafkaProducer with clientId $clientId"
        when (ex) {
            is IllegalStateException,
            is ProducerFencedException,
            is UnsupportedVersionException,
            is UnsupportedForMessageFormatException,
            is AuthorizationException,
            is InvalidProducerEpochException,
            is FencedInstanceIdException -> {
                throw CordaMessageAPIFatalException("FatalError occurred $errorString", ex)
            }
            is TimeoutException,
            is InterruptException,
                // Failure to commit here might be due to consumer kicked from group.
                // Return as intermittent to trigger retry
            is CommitFailedException,
            is KafkaException -> {
                if (canAbort) {
                    abortTransaction()
                }
                throw CordaMessageAPIIntermittentException("Error occurred $errorString", ex)
            }
            else -> {
                throw CordaMessageAPIFatalException("Unexpected error occurred $errorString", ex)
            }
        }
    }

    /**
     * Generate the consumer offsets for a given list of [records]
     */
    private fun getRecordListOffsets(
        records: List<ConsumerRecord<*, *>>,
        topicPrefix: String
    ): Map<TopicPartition, OffsetAndMetadata> {
        if (records.isEmpty()) {
            return mutableMapOf()
        }

        return records.fold(mutableMapOf()) { offsets, record ->
            val key = TopicPartition(topicPrefix + record.topic(), record.partition())
            val currentOffset = offsets[key]?.offset() ?: 0L
            if (currentOffset <= record.offset()) {
                offsets[key] = OffsetAndMetadata(record.offset() + 1)
            }
            offsets
        }
    }
}
