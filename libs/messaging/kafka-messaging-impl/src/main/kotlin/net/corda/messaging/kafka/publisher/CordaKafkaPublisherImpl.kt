package net.corda.messaging.kafka.publisher

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.messaging.kafka.producer.wrapper.CordaKafkaProducer
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PRODUCER_CLIENT_ID
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PRODUCER_CLOSE_TIMEOUT
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PRODUCER_TRANSACTIONAL_ID
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.TOPIC_PREFIX
import net.corda.messaging.kafka.utils.getStringOrNull
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.errors.AuthenticationException
import org.apache.kafka.common.errors.AuthorizationException
import org.apache.kafka.common.errors.InterruptException
import org.apache.kafka.common.errors.SerializationException
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger
import java.nio.ByteBuffer
import java.time.Duration
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
@Component
class CordaKafkaPublisherImpl(
    private val kafkaConfig: Config,
    private val cordaKafkaProducer: CordaKafkaProducer,
) : Publisher {

    private companion object {
        private val log: Logger = contextLogger()
        private val fatalSendExceptions =
            listOf(
                AuthenticationException::class.java, AuthorizationException::class.java,
                IllegalStateException::class.java, SerializationException::class.java
            )
    }

    private val closeTimeout = kafkaConfig.getLong(PRODUCER_CLOSE_TIMEOUT)
    private val topicPrefix = kafkaConfig.getString(TOPIC_PREFIX)
    private val transactionalId = kafkaConfig.getStringOrNull(PRODUCER_TRANSACTIONAL_ID)
    private val clientId = kafkaConfig.getString(PRODUCER_CLIENT_ID)

    /**
     * Publish a record.
     * Records are published via transactions if an [transactionalId] is configured
     * Publish will retry recoverable transaction related errors based on [kafkaConfig]
     * Any fatal errors are returned in the future as [CordaMessageAPIFatalException]
     * Any intermittent errors are returned in the future as [CordaMessageAPIIntermittentException]
     * If publish is a transaction, sends are executed synchronously and will return a future of size 1.
     */
    override fun publish(records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
        //Only allow keys as string for now. see CORE-1367
        records.forEach {
            if (it.key.javaClass != String::class.java) {
                val future = CompletableFuture.failedFuture<Unit>(CordaMessageAPIFatalException("Unsupported Key type, use a String."))
                log.error("Unsupported Key type, use a String")
                return listOf(future)
            }
        }

        val futures = mutableListOf<CompletableFuture<Unit>>()
        if (transactionalId != null) {
            futures.add(publishTransaction(records))
        } else {
            publishRecordsAsync(records, futures)
        }

        return futures
    }

    override fun publishToPartition(records: List<Pair<Int, Record<*, *>>>): List<CompletableFuture<Unit>> {
        //Only allow keys as string for now. see CORE-1367
        records.forEach { (_, record) ->
            if (record.key.javaClass != String::class.java) {
                val future = CompletableFuture.failedFuture<Unit>(CordaMessageAPIFatalException("Unsupported Key type, use a String."))
                log.error("Unsupported Key type, use a String.")
                return listOf(future)
            }
        }

        val futures = mutableListOf<CompletableFuture<Unit>>()
        if (transactionalId != null) {
            futures.add(publishTransactionWithPartitions(records))
        } else {
            publishRecordsToPartitionsAsync(records, futures)
        }

        return futures
    }

    /**
     * Publish list of [records] asynchronously with results stored in [futures]
     */
    private fun publishRecordsAsync(records: List<Record<*, *>>, futures: MutableList<CompletableFuture<Unit>>) {
        records.forEach {
            val fut = CompletableFuture<Unit>()
            futures.add(fut)
            cordaKafkaProducer.send(ProducerRecord(topicPrefix + it.topic, it.key, it.value)) { _, ex ->
                setFutureFromResponse(ex, fut, it.topic)
            }
        }
    }

    /**
     * Publish provided list of records to specific partitions asynchronously with results stored in [futures].
     */
    private fun publishRecordsToPartitionsAsync(recordsWithPartitions: List<Pair<Int, Record<*, *>>>,
                                                futures: MutableList<CompletableFuture<Unit>>) {
        recordsWithPartitions.forEach { (partition, record) ->
            val fut = CompletableFuture<Unit>()
            futures.add(fut)
            cordaKafkaProducer.send(ProducerRecord(topicPrefix + record.topic, partition, record.key, record.value)) { _, ex ->
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
            it.sendRecords(records)
        }
    }

    /**
     * Same as [publishTransaction] but publishing records to specific partitions.
     */
    private fun publishTransactionWithPartitions(recordsWithPartitions: List<Pair<Int, Record<*, *>>>): CompletableFuture<Unit> {
        return executeInTransaction {
            it.sendRecordsToPartitions(recordsWithPartitions)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun executeInTransaction(block: (CordaKafkaProducer) -> Unit): CompletableFuture<Unit> {
        val future = CompletableFuture<Unit>()

        try {
            cordaKafkaProducer.beginTransaction()
            block(cordaKafkaProducer)
            cordaKafkaProducer.commitTransaction()
            future.complete(Unit)
        } catch (ex: Exception) {
            when (ex) {
                is CordaMessageAPIIntermittentException -> {
                    logErrorAndSetFuture(
                        "Kafka producer clientId $clientId, instanceId $transactionalId, " +
                                "failed to send", ex, future, false
                    )
                }
                else -> {
                    logErrorAndSetFuture(
                        "Kafka producer clientId $clientId, instanceId $transactionalId, " +
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
        val message = "Kafka producer clientId $clientId, instanceId $transactionalId, " +
                "for topic $topic failed to send"
        when {
            (exception == null) -> {
                //transaction operation can still fail at commit stage  so do not set to true until it is committed
                if (transactionalId == null) {
                    future.complete(Unit)
                } else {
                    log.debug { "Asynchronous send completed completed successfully." }
                }
            }
            fatalSendExceptions.contains(exception::class.java) -> {
                log.error("$message. Fatal error occurred. Closing producer.", exception)
                future.completeExceptionally(CordaMessageAPIFatalException(message, exception))
                close()
            }
            exception is InterruptException || exception is KafkaException -> {
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
    @Suppress("TooGenericExceptionCaught")
    override fun close() {
        try {
            cordaKafkaProducer.close(Duration.ofMillis(closeTimeout))
        } catch (ex: Exception) {
            log.error("CordaKafkaPublisher failed to close producer safely. ClientId: $clientId", ex)
        }
    }

}

