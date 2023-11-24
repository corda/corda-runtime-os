package net.corda.messaging.publisher

import com.google.common.util.concurrent.ThreadFactoryBuilder
import net.corda.messagebus.api.configuration.ProducerConfig
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.exception.CordaMessageAPIProducerRequiresReset
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.messaging.config.ResolvedPublisherConfig
import net.corda.messaging.utils.toCordaProducerRecord
import net.corda.messaging.utils.toCordaProducerRecords
import net.corda.utilities.debug
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Publisher will use a [CordaProducer] to communicate with the message bus. Failed producers are closed and recreated.
 * Records are sent via transactions if the producer configured to be transactional.
 * 
 * Record values are serialized to [ByteBuffer] using [net.corda.schema.registry.AvroSchemaRegistry].
 * Record keys are serialized using whatever serializer has been configured for the message bus.
 * Producer will automatically attempt resends based on the config.
 * Any Exceptions thrown during publish are returned in a [CompletableFuture].
 */
internal class CordaPublisherImpl(
    private val config: ResolvedPublisherConfig,
    private val producerConfig: ProducerConfig,
    private val cordaProducerBuilder: CordaProducerBuilder
) : Publisher {

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private const val QUEUE_SIZE = 200

        private fun handleUncaughtException(thread: Thread, exception: Throwable) {
            log.error("Uncaught exception from ${thread.name}", exception)
        }
    }

    private var cordaProducer = cordaProducerBuilder.createProducer(producerConfig, config.messageBusConfig)

    // Support for publishing records in batches, saving on the Kafka round trip. 
    private data class Batch(val records: List<Record<*, *>>, val future: CompletableFuture<Unit>)
    private val queue = ArrayBlockingQueue<Batch>(QUEUE_SIZE)
    // We use a limited queue executor to ensure we only ever queue one new request if we are currently processing
    // an existing request. It is OK to discard the second request trying to join the queue, as processing of every request
    // will involve queue draining anyway.
    private val executor = ThreadPoolExecutor(
        1, 1,
        0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(1),
        ThreadFactoryBuilder()
            .setUncaughtExceptionHandler(::handleUncaughtException)
            .setNameFormat("corda-publisher-batch-%d")
            .setDaemon(true)
            .build(),
        ThreadPoolExecutor.DiscardPolicy()
    )
    
    /**
     * Publish a record.
     * Records are published via transactions if an [ResolvedPublisherConfig.transactional] is set.
     * Publish will retry recoverable transaction related errors based on the producer config.
     * Any fatal errors are returned in the future as [CordaMessageAPIFatalException]
     * Any intermittent errors are returned in the future as [CordaMessageAPIIntermittentException]
     * Note there is no contractual need to recreate the publisher under these circumstances because it resets itself by
     * closing and recreating a producer. Clients still hold responsibility for end to end error handling nevertheless. For
     * example, you might want to issue an error message to and end user, or retry the publish operation.
     * If publisher is transactional, sends are executed synchronously and will return a future of size 1.
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

    override fun batchPublish(records: List<Record<*, *>>): CompletableFuture<Unit> {
        if (!config.transactional) {
            throw CordaMessageAPIFatalException(
                "Cannot use the batch publish API unless the publisher is transactional. Client id: ${config.clientId}"
            )
        }
        val batchFuture = CompletableFuture<Unit>()
        queue.put(Batch(records, batchFuture))
        
        // Post publishing asynchronously and release a caller thread as soon as possible
        executor.execute {
            val batches = ArrayList<Batch>(QUEUE_SIZE)
            queue.drainTo(batches)
            // Ensure we only go to Kafka if there are records to publish, as empty transactions incur a performance
            // cost.
            if (batches.isNotEmpty()) {
                val publishFuture = publishTransaction(batches.flatMap { it.records })
                publishFuture.whenComplete { _, throwable ->
                    batches.forEach { batch ->
                        if (throwable != null) {
                            batch.future.completeExceptionally(throwable)
                        } else {
                            batch.future.complete(Unit)
                        }
                    }
                }
            }
        }
        return batchFuture
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
        futures: MutableList<CompletableFuture<Unit>>
    ) {
        recordsWithPartitions.forEach { (partition, record) ->
            val fut = CompletableFuture<Unit>()
            futures.add(fut)
            cordaProducer.send(record, partition) { ex ->
                setFutureFromResponse(ex, fut, record.topic)
            }
        }
    }

    /**
     * Send list of [records] as a transaction. It is not necessary to handle exceptions for each record send in a transaction
     * as this is handled by the [CordaProducer] commitTransaction operation. commitTransaction will execute all sends synchronously
     * and will fail to send all if any individual send fail.
     * @return future to track transaction success.
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

    /**
     * This is not a general retry mechanism, it's designed specifically to cope with transient network errors at the
     * producer level. If a producer fails to reach any broker it can fail even quite come time after the broker is
     * again reachable. To cope with these cases we allow a single intermittent error reported by the producer to be
     * retried. Further errors will be propagated back to the client who must deal with them appropriately.
     */
    private fun tryWithSingleRecoveryAttempt(block: () -> Unit) {
        try {
            block()
        } catch (ex: CordaMessageAPIProducerRequiresReset) {
            // Explicitly block this from being retried
            throw ex
        } catch (ex: CordaMessageAPIIntermittentException) {
            log.warn("Attempting a single transaction retry")
            block()
        }
    }

    @Synchronized
    private fun executeInTransaction(block: (CordaProducer) -> Unit): CompletableFuture<Unit> {
        val future = CompletableFuture<Unit>()
        try {
            tryWithSingleRecoveryAttempt {
                cordaProducer.beginTransaction()
                block(cordaProducer)
                cordaProducer.commitTransaction()
                future.complete(Unit)
            }
        } catch (ex: Exception) {
            when (ex) {
                is CordaMessageAPIProducerRequiresReset -> {
                    logErrorAndSetFuture(
                        "Producer clientId ${config.clientId}, transactional ${config.transactional}, " +
                                "failed to send, resetting producer", ex, future, false
                    )
                    resetProducer()
                }

                is CordaMessageAPIIntermittentException -> {
                    logErrorAndSetFuture(
                        "Producer clientId ${config.clientId}, transactional ${config.transactional}, " +
                                "failed to send", ex, future, false
                    )
                }

                else -> {
                    logErrorAndSetFuture(
                        "Producer clientId ${config.clientId}, transactional ${config.transactional}, " +
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
        val message = "Producer clientId ${config.clientId}, transactional ${config.transactional}, " +
                "for topic $topic failed to send"
        when (exception) {
            null -> {
                //transaction operation can still fail at commit stage  so do not set to true until it is committed
                if (!config.transactional) {
                    future.complete(Unit)
                } else {
                    log.debug { "Asynchronous send completed successfully." }
                }
            }

            is CordaMessageAPIFatalException -> {
                log.warn("$message. Fatal producer error occurred.", exception)
                future.completeExceptionally(CordaMessageAPIFatalException(message, exception))
            }

            is CordaMessageAPIIntermittentException -> {
                log.warn(message, exception)
                future.completeExceptionally(CordaMessageAPIIntermittentException(message, exception))
            }

            else -> {
                log.warn("$message. Unknown error occurred.", exception)
                future.completeExceptionally(CordaMessageAPIFatalException(message, exception))
            }
        }
    }

    /**
     * Log the [message] and [exception]. Set the [exception] to the [future].
     */
    private fun logErrorAndSetFuture(
        message: String,
        exception: Exception,
        future: CompletableFuture<Unit>,
        fatal: Boolean
    ) {
        if (fatal) {
            log.error("$message. Fatal error, publisher cannot continue.", exception)
            future.completeExceptionally(CordaMessageAPIFatalException(message, exception))
        } else {
            log.info(message, exception)
            future.completeExceptionally(CordaMessageAPIIntermittentException(message, exception))
        }
    }

    override fun close() {
        closeProducerAndSuppressExceptions()
        executor.shutdown()
    }

    /**
     * Close the producer and instantiate a new one. The producer is unknown to clients of this class, there is no way
     * for them to know it has been closed and is no longer usable, so we must ensure there is always one available.
     * The producer should only be closed for good if the public [close] method is called.
     */
    private fun resetProducer() {
        closeProducerAndSuppressExceptions()
        cordaProducer = cordaProducerBuilder.createProducer(producerConfig, config.messageBusConfig)
    }

    /**
     * If an exception is thrown whilst closing, swallow the error to avoid double exceptions.
     */
    private fun closeProducerAndSuppressExceptions() {
        try {
            cordaProducer.close()
        } catch (ex: Exception) {

            log.warn("CordaPublisherImpl failed to close producer safely. ClientId: ${config.clientId}", ex)
        }
    }
}
