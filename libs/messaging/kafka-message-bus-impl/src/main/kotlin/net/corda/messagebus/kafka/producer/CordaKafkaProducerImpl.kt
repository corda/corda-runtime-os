package net.corda.messagebus.kafka.producer

import io.micrometer.core.instrument.binder.MeterBinder
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messagebus.kafka.config.ResolvedProducerConfig
import net.corda.messagebus.kafka.consumer.CordaKafkaConsumerImpl
import net.corda.messagebus.kafka.utils.toKafkaRecord
import net.corda.messagebus.kafka.utils.toKafkaRecords
import net.corda.messaging.api.chunking.ChunkSerializerService
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.exception.CordaMessageAPIProducerRequiresReset
import net.corda.metrics.CordaMetrics
import net.corda.tracing.TraceContext
import net.corda.tracing.addTraceContextToRecord
import net.corda.tracing.getOrCreateBatchPublishTracing
import net.corda.tracing.traceSend
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.Producer
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
import org.slf4j.LoggerFactory

/**
 * Wrapper for the CordaKafkaProducer.
 * Delegate actions to kafka [producer].
 * Wrap calls to [producer] with error handling.
 */
@Suppress("TooManyFunctions")
class CordaKafkaProducerImpl(
    private val config: ResolvedProducerConfig,
    private val producer: Producer<Any, Any>,
    private val chunkSerializerService: ChunkSerializerService,
    private val producerMetricsBinder: MeterBinder,
) : CordaProducer {
    private val topicPrefix = config.topicPrefix
    private val transactional = config.transactional
    private val clientId = config.clientId

    init {
        producerMetricsBinder.bindTo(CordaMetrics.registry)

        if (transactional) {
            initTransactionForProducer()
        }
    }

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val asyncChunkErrorMessage = "Tried to send record which requires chunking using an asynchronous producer"

        val fatalExceptions: Set<Class<out Throwable>> = setOf(
            UnsupportedVersionException::class.java,
            UnsupportedForMessageFormatException::class.java,
            AuthorizationException::class.java,
            FencedInstanceIdException::class.java
        )
        val transientExceptions: Set<Class<out Throwable>> = setOf(
            TimeoutException::class.java,
            InterruptException::class.java,
            // Failure to commit here might be due to consumer kicked from group.
            // Return as intermittent to trigger retry
            InvalidProducerEpochException::class.java,
            // See https://cwiki.apache.org/confluence/display/KAFKA/KIP-588%3A+Allow+producers+to+recover+gracefully+from+transaction+timeouts
            // This exception means the coordinator has bumped the producer epoch because of a timeout of this producer.
            // There is no other producer, we are not a zombie, and so don't need to be fenced, we can simply abort and retry.
            KafkaException::class.java
        )
        val ApiExceptions: Set<Class<out Throwable>> = setOf(
            CordaMessageAPIFatalException::class.java,
            CordaMessageAPIIntermittentException::class.java
        )
    }

    private fun toTraceKafkaCallback(callback: CordaProducer.Callback, ctx: TraceContext): Callback {
        return Callback { m, ex ->
            ctx.markInScope().use {
                ctx.traceTag("send.offset", m.offset().toString())
                ctx.traceTag("send.partition", m.partition().toString())
                ctx.traceTag("send.topic", m.topic())
                callback.onCompletion(ex)
                if (ex != null) {
                    ctx.errorAndFinish(ex)
                } else {
                    ctx.finish()
                }
            }
        }
    }

    override fun send(record: CordaProducerRecord<*, *>, callback: CordaProducer.Callback?) {
        getOrCreateBatchPublishTracing(clientId).begin(listOf(record.headers))
        tryWithCleanupOnFailure("send single record, no partition") {
            sendRecord(record, callback)
        }
    }

    override fun send(record: CordaProducerRecord<*, *>, partition: Int, callback: CordaProducer.Callback?) {
        getOrCreateBatchPublishTracing(clientId).begin(listOf(record.headers))
        tryWithCleanupOnFailure("send single record, with partition") {
            sendRecord(record, callback, partition)
        }
    }

    override fun sendRecords(records: List<CordaProducerRecord<*, *>>) {
        getOrCreateBatchPublishTracing(clientId).begin(records.map { it.headers })
        tryWithCleanupOnFailure("send multiple records, no partition") {
            for (record in records) {
                sendRecord(record)
            }
        }
    }

    override fun sendRecordsToPartitions(recordsWithPartitions: List<Pair<Int, CordaProducerRecord<*, *>>>) {
        val tracing = getOrCreateBatchPublishTracing(clientId)
        tracing.begin(recordsWithPartitions.map { it.second.headers })
        tryWithCleanupOnFailure("send multiple records, with partitions") {
            for ((partition, record) in recordsWithPartitions) {
                sendRecord(record, null, partition)
            }
        }
    }

    /**
     * Check to see if the [record] needs chunking. If it does then check producer type and send chunks,
     * otherwise send the records normally
     * @param record record to send
     * @param callback for error handling in async producers
     * @param partition partition to send to. defaults to null.
     */
    private fun sendRecord(record: CordaProducerRecord<*, *>, callback: CordaProducer.Callback? = null, partition: Int? = null) {
        val chunkedRecords = chunkSerializerService.generateChunkedRecords(record)
        if (chunkedRecords.isNotEmpty()) {
            sendChunks(chunkedRecords, callback, partition)
        } else {
            sendWholeRecord(record, partition, callback)
        }
    }

    private fun sendWholeRecord(
        record: CordaProducerRecord<*, *>,
        partition: Int?,
        callback: CordaProducer.Callback?
    ) {
        val traceContext = traceSend(record.headers, "kafka producer - send record to topic ${record.topic}")

        traceContext.markInScope().use {
            try {
                producer.send(
                    addTraceContextToRecord(record).toKafkaRecord(topicPrefix, partition),
                    toTraceKafkaCallback({ exception -> callback?.onCompletion(exception) }, traceContext)
                )
            } catch (ex: CordaRuntimeException) {
                traceContext.errorAndFinish(ex)
                val msg = "Failed to send record to topic ${record.topic} with key ${record.key}"
                if (config.throwOnSerializationError) {
                    log.error(msg, ex)
                    throw ex
                } else {
                    log.warn(msg, ex)
                }
            } catch (ex: Exception) {
                traceContext.errorAndFinish(ex)
                throw ex
            }
        }
    }

    /**
     * Send chunked records via the kafka producer.
     * If the producer is configured to be asynchronous, throw a fatal exception as this is not allowed.
     * Async producers may fail to send some chunks which could block the consumer from returning records
     * due it waiting for more chunks to arrive.
     * Executes the callback if there is an error to mimic kafka producers error handling where it also sets the callback when there are
     * errors always.
     * @param cordaProducerRecords chunked records to send
     * @param callback for error handling in async producers
     * @param partition partition to send to. defaults to null.
     */
    private fun sendChunks(
        cordaProducerRecords: List<CordaProducerRecord<*, *>>,
        callback: CordaProducer.Callback? = null,
        partition: Int? = null
    ) {
        if (!transactional) {
            //set the call back and throw the exception. This mimics what the kafka client does
            val exceptionThrown = CordaMessageAPIFatalException(asyncChunkErrorMessage)
            callback?.onCompletion(exceptionThrown)
            throw exceptionThrown
        }

        recordChunksCountPerTopic(cordaProducerRecords)

        cordaProducerRecords.forEach {
            //note callback is only applicable to async calls which are not allowed
            producer.send(it.toKafkaRecord(topicPrefix, partition))
        }
    }

    private fun recordChunksCountPerTopic(cordaProducerRecords: List<CordaProducerRecord<*, *>>) {
        cordaProducerRecords.groupBy { it.topic }
            .mapValues { (_, records) -> records.size }
            .forEach { (topic, count) ->
                CordaMetrics.Metric.Messaging.ProducerChunksGenerated.builder()
                    .withTag(CordaMetrics.Tag.MessagePatternClientId, config.clientId)
                    .withTag(CordaMetrics.Tag.Topic, topic)
                    .build()
                    .record(count.toDouble())
            }

    }

    override fun beginTransaction() {
        tryWithCleanupOnFailure("beginning transaction", abortTransactionOnFailure = false) {
            producer.beginTransaction()
        }
    }

    override fun abortTransaction() {
        getOrCreateBatchPublishTracing(config.clientId).abort()
        tryWithCleanupOnFailure("aborting transaction", abortTransactionOnFailure = false) {
            producer.abortTransaction()
        }
    }

    override fun commitTransaction() {
        var retryableException: KafkaException? = null

        tryWithCleanupOnFailure("committing transaction") {
            retryableException = commitTransactionAndCatchRetryable()

            if (retryableException != null) {
                // We can/should retry this kind of failure under the contract of the Kafka producer, abort is neither
                // required nor allowed. We allow a single retry only.
                log.warn("Unexpected transient error committing transaction, re-trying", retryableException)
                retryableException = commitTransactionAndCatchRetryable()
            }
        }

        getOrCreateBatchPublishTracing(config.clientId).complete()

        if (retryableException != null) {
            // We have retried once, we are not retrying again, so the only other option compatible with the producer
            // contract is to close the producer without aborting. That is the responsibility of the client, which we
            // notify by throwing the relevant exception.
            throw CordaMessageAPIProducerRequiresReset(
                "Unexpected error occurred committing transaction",
                retryableException
            )
        }
    }

    /**
     * The contract of the Kafka producer is that certain types of errors have their own process for handling.
     * If a commit is interrupted or timed out, we cannot abort, but it is safe to retry the commit if we want.
     * This method catches those exceptions and returns them if they happened.
     *
     * @return null if successful, exception instance transaction can be retried, otherwise throws whatever thrown by the producer.
     */
    private fun commitTransactionAndCatchRetryable() = try {
        producer.commitTransaction()
        null
    } catch (ex: TimeoutException) {
        ex
    } catch (ex: InterruptException) {
        ex
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
            log.info(
                "CordaKafkaProducer failed to close producer safely. This can be observed when there are " +
                        "no reachable brokers. ClientId: ${config.clientId}", ex
            )
        } finally {
            (producerMetricsBinder as? AutoCloseable)?.close()
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
            // transactional publications will be completed in commitTransaction()
            if (!transactional) {
                getOrCreateBatchPublishTracing(config.clientId).complete()
            }
        } catch (ex: Exception) {
            getOrCreateBatchPublishTracing(config.clientId).abort()
            handleException(ex, operation, abortTransactionOnFailure)
        }
    }

    @Suppress("ThrowsCount")
    private fun handleException(ex: Exception, operation: String, abortTransaction: Boolean) {
        val errorString = "$operation for CordaKafkaProducer with clientId ${config.clientId}"
        when (ex::class.java) {
            in fatalExceptions -> {
                throw CordaMessageAPIFatalException("FatalError occurred $errorString", ex)
            }

            in transientExceptions -> {
                if (abortTransaction) {
                    abortTransaction()
                }
                throw CordaMessageAPIIntermittentException("Error occurred $errorString", ex)
            }

            in ApiExceptions -> { throw ex }

            IllegalStateException::class.java -> {
                // It's not clear whether the producer is ok to abort and continue or not in this case, so play it safe
                // and let the client know to create a new one.
                throw CordaMessageAPIProducerRequiresReset("Error occurred $errorString", ex)
            }

            ProducerFencedException::class.java -> {
                // There are two scenarios in which a ProducerFencedException can be thrown:
                //
                // 1. The producer is fenced because another producer with the same transactional.id has been started.
                // 2. The producer is fenced due to a timeout on the broker side.
                //
                // There should be no way for another producer to be started with the same transactional.id, so we can
                // assume that the producer is fenced because of a timeout and trigger a reset.
                throw CordaMessageAPIProducerRequiresReset(
                    "ProducerFencedException thrown, likely due to a timeout on the broker side. " +
                            "Triggering a reset of the producer. $errorString", ex
                )
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
