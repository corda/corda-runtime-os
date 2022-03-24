package net.corda.messaging.publisher

import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.messaging.config.ResolvedPublisherConfig
import net.corda.messaging.utils.toCordaProducerRecord
import net.corda.messaging.utils.toCordaProducerRecords
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.slf4j.Logger
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture

/**
 * Kafka publisher will create a new Kafka instance of Publisher.
 * Publisher will use a kafka [producer] to communicate with kafka.
 * Records are sent via transactions if the instanceId provided in the configuration is not null.
 * Record values are serialized to [ByteBuffer] using [avroSchemaRegistry]
 * Record keys are serialized using kafka configured serializer.
 * Producer will automatically attempt resends based on [kafkaConfig].
 * Any Exceptions thrown during publish are returned in a [CompletableFuture]
 */
internal class CordaPublisherImpl(
    private val config: ResolvedPublisherConfig,
    private val cordaProducer: CordaProducer,
) : Publisher {

    private companion object {
        private val log: Logger = contextLogger()
    }

    /**
     * Publish a record.
     * Records are published via transactions if an [transactionalId] is configured
     * Publish will retry recoverable transaction related errors based on [kafkaConfig]
     * Any fatal errors are returned in the future as [CordaMessageAPIFatalException]
     * Any intermittent errors are returned in the future as [CordaMessageAPIIntermittentException]
     * If publish is a transaction, sends are executed synchronously and will return a future of size 1.
     */
    override fun publish(records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
        val futures = mutableListOf<CompletableFuture<Unit>>()
        if (config.transactional) {
            futures.add(publishTransaction(records))
        } else {
            publishRecordsAsync(records, futures)
        }

        return futures
    }

    override fun publishToPartition(records: List<Pair<Int, Record<*, *>>>): List<CompletableFuture<Unit>> {

        val cordaRecords = records.map { Pair(it.first, it.second.toCordaProducerRecord()) }
        val futures = mutableListOf<CompletableFuture<Unit>>()
        if (config.transactional) {
            futures.add(publishTransactionWithPartitions(cordaRecords))
        } else {
            publishRecordsToPartitionsAsync(cordaRecords, futures)
        }

        return futures
    }
    /**
     * Publish list of [records] asynchronously with results stored in [futures]
     */
    private fun publishRecordsAsync(records: List<Record<*, *>>, futures: MutableList<CompletableFuture<Unit>>) {
        records.toCordaProducerRecords().forEach {
            val fut = CompletableFuture<Unit>()
            futures.add(fut)
            cordaProducer.send(it) { ex ->
                setFutureFromResponse(ex, fut, it.topic)
            }
        }
    }

    /**
     * Publish provided list of records to specific partitions asynchronously with results stored in [futures].
     */
    private fun publishRecordsToPartitionsAsync(
        recordsWithPartitions: List<Pair<Int, CordaProducerRecord<*, *>>>,
        futures: MutableList<CompletableFuture<Unit>>) {
        recordsWithPartitions.forEach { (partition, record) ->
            val fut = CompletableFuture<Unit>()
            futures.add(fut)
            cordaProducer.send(record, partition) { ex ->
                setFutureFromResponse(ex, fut, record.topic)
            }
        }
    }

    /**
     * Send list of [records] as a transaction. It is not necessary to handle exceptions for each send in a transaction
     * as this is handled by the [KafkaProducer] commitTransaction operation. commitTransaction will execute all sends synchronously
     * and will fail to send all if any individual sends fail
     * Set the [future] with the result of the transaction.
     * @return future set to true if transaction was successful.
     */
    private fun publishTransaction(records: List<Record<*, *>>): CompletableFuture<Unit> {
        return executeInTransaction {
            it.sendRecords(records.toCordaProducerRecords())
        }
    }

    /**
     * Same as [publishTransaction] but publishing records to specific partitions.
     */
    private fun publishTransactionWithPartitions(
        recordsWithPartitions: List<Pair<Int, CordaProducerRecord<*, *>>>
    ): CompletableFuture<Unit> {
        return executeInTransaction {
            it.sendRecordsToPartitions(recordsWithPartitions)
        }
    }

    @Synchronized
    private fun executeInTransaction(block: (CordaProducer) -> Unit): CompletableFuture<Unit> {
        val future = CompletableFuture<Unit>()

        try {
            cordaProducer.beginTransaction()
            block(cordaProducer)
            cordaProducer.commitTransaction()
            future.complete(Unit)
        } catch (ex: Exception) {
            when (ex) {
                is CordaMessageAPIIntermittentException -> {
                    logErrorAndSetFuture(
                        "Kafka producer clientId ${config.clientId}, transactional ${config.transactional}, " +
                                "failed to send", ex, future, false
                    )
                }
                else -> {
                    logErrorAndSetFuture(
                        "Kafka producer clientId ${config.clientId}, transactional ${config.transactional}, " +
                                "failed to send", ex, future, true
                    )
                }
            }
        }

        return future
    }

    /**
     * Helper function to set a [future] result based on the presence of an [exception]
     */
    private fun setFutureFromResponse(exception: Exception?, future: CompletableFuture<Unit>, topic: String) {
        val message = "Kafka producer clientId ${config.clientId}, transactional ${config.transactional}, " +
                "for topic $topic failed to send"
        when {
            (exception == null) -> {
                //transaction operation can still fail at commit stage  so do not set to true until it is committed
                if (!config.transactional) {
                    future.complete(Unit)
                } else {
                    log.debug { "Asynchronous send completed completed successfully." }
                }
            }
            exception is CordaMessageAPIFatalException -> {
                log.error("$message. Fatal error occurred. Closing producer.", exception)
                future.completeExceptionally(CordaMessageAPIFatalException(message, exception))
                close()
            }
            exception is CordaMessageAPIIntermittentException -> {
                log.warn(message, exception)
                future.completeExceptionally(CordaMessageAPIIntermittentException(message, exception))
            }
            else -> {
                log.error("$message. Unknown error occurred. Closing producer.", exception)
                future.completeExceptionally(CordaMessageAPIFatalException(message, exception))
                close()
            }
        }
    }

    /**
     * Log the [message] and [exception]. Set the [exception] to the [future].
     * If [fatal] is set to true then the producer is closed safely.
     */
    private fun logErrorAndSetFuture(
        message: String,
        exception: Exception,
        future: CompletableFuture<Unit>,
        fatal: Boolean
    ) {
        if (fatal) {
            log.error("$message. Closing producer.", exception, future)
            future.completeExceptionally(CordaMessageAPIFatalException(message, exception))
            close()
        } else {
            log.warn(message, exception, future)
            future.completeExceptionally(CordaMessageAPIIntermittentException(message, exception))
        }
    }

    /**
     * Safely close a producer. If an exception is thrown swallow the error to avoid double exceptions
     */
    override fun close() {
        try {
            cordaProducer.close()
        } catch (ex: Exception) {
            log.error("CordaKafkaPublisher failed to close producer safely. ClientId: ${config.clientId}", ex)
        }
    }

}

