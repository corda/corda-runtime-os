package net.corda.messagebus.kafka.producer

import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messagebus.kafka.config.ResolvedProducerConfig
import net.corda.messagebus.kafka.consumer.CordaKafkaConsumerImpl
import net.corda.messagebus.kafka.utils.toKafkaRecords
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.exception.CordaMessageAPIProducerRequiresReset
import net.corda.v5.base.util.contextLogger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.Callback
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

/**
 * Wrapper for the CordaKafkaProducer.
 * Delegate actions to kafka [producer].
 * Wrap calls to [producer] with error handling.
 */
@Suppress("TooManyFunctions")
class CordaKafkaProducerImpl(
    private val config: ResolvedProducerConfig,
    private val producer: Producer<Any, Any>
) : CordaProducer {
    private val topicPrefix = config.topicPrefix
    private val transactional = config.transactional

    init {
        if (transactional) {
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
        tryWithCleanupOnFailure("send single record, no partition") {
            val prefixedRecord =
                ProducerRecord(topicPrefix + record.topic, record.key, record.value)
            producer.send(prefixedRecord, callback?.toKafkaCallback())
        }
    }

    override fun send(record: CordaProducerRecord<*, *>, partition: Int, callback: CordaProducer.Callback?) {
        tryWithCleanupOnFailure("send single record, with partition") {
            val prefixedRecord =
                ProducerRecord(topicPrefix + record.topic, partition, record.key, record.value)
            producer.send(prefixedRecord, callback?.toKafkaCallback())
        }
    }

    override fun sendRecords(records: List<CordaProducerRecord<*, *>>) {
        tryWithCleanupOnFailure("send multiple records, no partition") {
            for (record in records) {
                producer.send(ProducerRecord(topicPrefix + record.topic, record.key, record.value))
            }
        }
    }

    override fun sendRecordsToPartitions(recordsWithPartitions: List<Pair<Int, CordaProducerRecord<*, *>>>) {
        tryWithCleanupOnFailure("send multiple records, with partitions") {
            for ((partition, record) in recordsWithPartitions) {
                producer.send(ProducerRecord(topicPrefix + record.topic, partition, record.key, record.value))
            }
        }
    }

    override fun beginTransaction() {
        tryWithCleanupOnFailure("beginning transaction", abortTransactionOnFailure = false) {
            producer.beginTransaction()
        }
    }

    override fun abortTransaction() {
        tryWithCleanupOnFailure("aborting transaction", abortTransactionOnFailure = false) {
            producer.abortTransaction()
        }
    }

    override fun commitTransaction() {
        var failedDueToRetryable = false
        tryWithCleanupOnFailure("committing transaction") {
            if (!commitTransactionAndCatchRetryable()) {
                // We can/should retry this kind of failure under the contract of the Kafka producer, abort is neither
                // required nor allowed. We allow a single retry only.
                failedDueToRetryable = !commitTransactionAndCatchRetryable()
            }
        }

        if (failedDueToRetryable) {
            // We have retired once, we are not retrying again, so the only other option compatible with the producer
            // contract is to close the producer without aborting. That is the responsibility of the client, which we
            // notify by throwing the relevant exception.
            throw CordaMessageAPIProducerRequiresReset("Unexpected error occurred committing transaction")
        }
    }

    /**
     * The contract of the Kafka producer is that certain types of errors have their own process for handling.
     * If a commit is interrupted or timed out, we cannot abort, but it is safe to retry the commit if we want.
     * This method catches those exceptions and returns whether that happened or not.
     *
     * @return true if successful, false if not and can be retried, otherwise throws whatever the producer throws
     */
    private fun commitTransactionAndCatchRetryable() = try {
        producer.commitTransaction()
        true
    } catch (ex: TimeoutException) {
        false
    } catch (ex: InterruptException) {
        false
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

    private fun trySendOffsetsToTransaction(
        consumer: CordaConsumer<*, *>,
        records: List<ConsumerRecord<*, *>>? = null
    ) {
        tryWithCleanupOnFailure("sending offset for transaction") {
            producer.sendOffsetsToTransaction(
                consumerOffsets(consumer, records),
                (consumer as CordaKafkaConsumerImpl).groupMetadata()
            )
        }
    }

    /**
     * Safely close a producer. If an exception is thrown swallow the error to avoid double exceptions
     */
    override fun close() {
        try {
            producer.close()
        } catch (ex: Exception) {
            log.info("CordaKafkaProducer failed to close producer safely. This can be observed when there are " +
                    "no reachable brokers. ClientId: ${config.clientId}", ex)
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
        tryWithCleanupOnFailure("initializing producer for transactions", abortTransactionOnFailure = false) {
            producer.initTransactions()
        }
    }

    private fun tryWithCleanupOnFailure(
        operation: String,
        abortTransactionOnFailure: Boolean = transactional,
        block: () -> Unit
    ) {
        try {
            block()
        } catch (ex: Exception) {
            handleException(ex, operation, abortTransactionOnFailure)
        }
    }

    @Suppress("ThrowsCount")
    private fun handleException(ex: Exception, operation: String, abortTransaction: Boolean) {
        val errorString = "$operation for CordaKafkaProducer with clientId ${config.clientId}"
        when (ex) {
            is ProducerFencedException,
            is UnsupportedVersionException,
            is UnsupportedForMessageFormatException,
            is AuthorizationException,
            is FencedInstanceIdException -> {
                throw CordaMessageAPIFatalException("FatalError occurred $errorString", ex)
            }

            is IllegalStateException -> {
                // It's not clear whether the producer is ok to abort and continue or not in this case, so play it safe
                // and let the client know to create a new one.
                throw CordaMessageAPIProducerRequiresReset("Error occurred $errorString", ex)
            }

            is TimeoutException,
            is InterruptException,
                // Failure to commit here might be due to consumer kicked from group.
                // Return as intermittent to trigger retry
            is InvalidProducerEpochException,
                // See https://cwiki.apache.org/confluence/display/KAFKA/KIP-588%3A+Allow+producers+to+recover+gracefully+from+transaction+timeouts
                // This exception means the coordinator has bumped the producer epoch because of a timeout of this producer.
                // There is no other producer, we are not a zombie, and so don't need to be fenced, we can simply abort and retry.
            is KafkaException -> {
                if (abortTransaction) {
                    abortTransaction()
                }
                throw CordaMessageAPIIntermittentException("Error occurred $errorString", ex)
            }

            else -> {
                // Here we do not know what the exact cause of the exception is, but we do know Kafka has not told us we
                // must close down, nor has it told us we can abort and retry. In this instance the most sensible thing
                // for the client to do would be to close this producer and create a new one.
                throw CordaMessageAPIProducerRequiresReset("Unexpected error occurred $errorString", ex)
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
