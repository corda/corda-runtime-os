package net.corda.messaging.kafka.publisher

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.kafka.producer.wrapper.CordaKafkaProducer
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.KAFKA_TOPIC_PREFIX
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PRODUCER_CLOSE_TIMEOUT
import net.corda.v5.base.concurrent.CordaFuture
import net.corda.v5.base.internal.concurrent.OpenFuture
import net.corda.v5.base.internal.concurrent.openFuture
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

/**
 * Kafka publisher will create a new Kafka instance of Publisher.
 * Publisher will use a kafka [producer] to communicate with kafka.
 * Records are sent via transactions if [publisherConfig].instanceId is not null.
 * Record values are serialized to [ByteBuffer] using [avroSchemaRegistry]
 * Record keys are serialized using kafka configured serializer.
 * Producer will automatically attempt resends based on [kafkaConfig].
 * Any Exceptions thrown during publish are returned in a CordaFuture.
 */
@Component
class CordaKafkaPublisherImpl (
    private val publisherConfig: PublisherConfig,
    private val kafkaConfig: Config,
    private val cordaKafkaProducer: CordaKafkaProducer,
    ) : Publisher {

    private companion object {
        private val log: Logger = contextLogger()
        private val fatalSendExceptions = listOf(AuthenticationException::class.java, AuthorizationException::class.java,
            IllegalStateException::class.java, SerializationException::class.java)
    }

    private val closeTimeout = kafkaConfig.getLong(PRODUCER_CLOSE_TIMEOUT)
    private val topicPrefix = kafkaConfig.getString(KAFKA_TOPIC_PREFIX)
    private val instanceId = publisherConfig.instanceId
    private val clientId = publisherConfig.clientId

    /**
     * Publish a record.
     * Records are published via transactions if an [instanceId] is configured in the [publisherConfig]
     * Publish will retry recoverable transaction related errors based on [kafkaConfig]
     * Any fatal errors are returned in the future as [CordaMessageAPIFatalException]
     * Any intermittent errors are returned in the future as [CordaMessageAPIIntermittentException]
     * If publish is a transaction, sends are executed synchronously and will return a future of size 1.
     */
    override fun publish(records: List<Record<*, *>>): List<CordaFuture<Unit>> {
        val futures = mutableListOf<CordaFuture<Unit>>()
        if (publisherConfig.instanceId != null) {
            futures.add(publishTransaction(records))
        } else {
            publishRecordsAsync(records, futures)
        }

        return futures
    }

    /**
     * Publish list of [records] asynchronously with results stored in [futures]
     */
    private fun publishRecordsAsync(records: List<Record<*, *>>, futures: MutableList<CordaFuture<Unit>>) {
        records.forEach {
            val fut = openFuture<Unit>()
            futures.add(fut)
            cordaKafkaProducer.send(ProducerRecord(topicPrefix + it.topic, it.key, it.value)) { _, ex ->
                setFutureFromResponse(ex, fut, it.topic)
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
    @Suppress("TooGenericExceptionCaught")
    private fun publishTransaction(records: List<Record<*, *>>): CordaFuture<Unit> {
        val fut = openFuture<Unit>()

        try {
            cordaKafkaProducer.beginTransaction()
            cordaKafkaProducer.sendRecords(records)
            cordaKafkaProducer.tryCommitTransaction()
            fut.set(Unit)

        } catch (ex: Exception) {
            when(ex) {
                is CordaMessageAPIIntermittentException -> {
                    logErrorAndSetFuture("Kafka producer clientId $clientId, instanceId $instanceId, " +
                            "failed to send", ex, fut, false)
                }
                else -> {
                    logErrorAndSetFuture("Kafka producer clientId $clientId, instanceId $instanceId, " +
                            "failed to send", ex, fut, true)
                }
            }
        }

        return fut
    }

    /**
     * Helper function to set a [future] result based on the presence of an [exception]
     */
    private fun setFutureFromResponse(exception: Exception?, future: OpenFuture<Unit>, topic: String) {
        val message = "Kafka producer clientId $clientId, instanceId $instanceId, " +
                "for topic $topic failed to send"
        when {
            (exception == null) -> {
                //transaction operation can still fail at commit stage  so do not set to true until it is committed
                if (instanceId == null) {
                    future.set(Unit)
                } else {
                    log.debug { "Asynchronous send completed completed successfully." }
                }
            }
            fatalSendExceptions.contains(exception::class.java) -> {
                log.error("$message. Fatal error occurred. Closing producer.", exception)
                future.setException(CordaMessageAPIFatalException(message, exception))
                close()
            }
            exception is InterruptException || exception is KafkaException -> {
                log.warn(message, exception)
                future.setException(CordaMessageAPIIntermittentException(message, exception))
            }
            else -> {
                log.error("$message. Unknown error occurred. Closing producer.", exception)
                future.setException(CordaMessageAPIFatalException(message, exception))
                close()
            }
        }
    }

    /**
     * Log the [message] and [exception]. Set the [exception] to the [future].
     * If [fatal] is set to true then the producer is closed safely.
     */
    private fun logErrorAndSetFuture(message: String, exception: Exception, future: OpenFuture<Unit>, fatal: Boolean) {
        if (fatal) {
            log.error("$message. Closing producer.", exception, future)
            future.setException(CordaMessageAPIFatalException(message, exception))
            close()
        } else {
            log.warn(message, exception, future)
            future.setException(CordaMessageAPIIntermittentException(message, exception))
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

    override fun publishToPartition(records: List<Pair<Int, Record<*, *>>>): List<CordaFuture<Unit>> {
        TODO("Not yet implemented")
    }

}
