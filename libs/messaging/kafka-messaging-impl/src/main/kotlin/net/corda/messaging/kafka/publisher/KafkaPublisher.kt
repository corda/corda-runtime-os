package net.corda.messaging.kafka.publisher

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PRODUCER_CLOSE_TIMEOUT
import net.corda.messaging.kafka.utils.toProducerRecord
import net.corda.v5.base.concurrent.CordaFuture
import net.corda.v5.base.internal.concurrent.OpenFuture
import net.corda.v5.base.internal.concurrent.openFuture
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.errors.AuthorizationException
import org.apache.kafka.common.errors.InterruptException
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.errors.TimeoutException
import org.apache.kafka.common.errors.ProducerFencedException
import org.apache.kafka.common.errors.InvalidProducerEpochException
import org.apache.kafka.common.errors.AuthenticationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Kafka publisher will create a new KafkaProducer instance of KafkaPublisher.
 * Records are sent via transactions if [publisherConfig].instanceId is not null.
 * Producer will automatically attempt resends. If instanceId is set resends will have exactly once semantics.
 * to ensure no more than 1 message is delivered.
 * Any Exceptions thrown during publish are returned in a CordaFuture.
 */
class KafkaPublisher<K, V>(
    private val publisherConfig: PublisherConfig,
    private val kafkaConfig: Config,
    private val producer: Producer<K, V>) : Publisher<K, V> {

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    private val closeTimeout = kafkaConfig.getLong(PRODUCER_CLOSE_TIMEOUT)

    override fun publish(record: Record<K, V>): CordaFuture<Boolean> {
        val fut = openFuture<Boolean>()
        val instanceId = publisherConfig.instanceId
        try {
            if (instanceId != null) {
                producer.beginTransaction()
            }

            producer.send(record.toProducerRecord()) { it, ex ->
                setFutureFromResponse(ex, fut)
            }

            if (instanceId != null) {
                producer.commitTransaction()
            }
        } catch (ex: IllegalStateException) {
            logErrorAndSetFuture("Kafka producer clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                    "for topic ${publisherConfig.topic} failed to send. Producer is in an illegal state. Closing producer", ex, fut, true)
        } catch (ex: ProducerFencedException) {
            logErrorAndSetFuture("Kafka producer clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                    "for topic ${publisherConfig.topic} failed to send. Another producer with the same transactional.id is active. " +
                    "Closing publisher.", ex, fut, true)
        } catch (ex: InvalidProducerEpochException) {
            val message ="Kafka producer clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                    "for topic ${publisherConfig.topic} failed to send. request sent to the partition leader contains " +
                    "a non-matching producer epoch."
            logErrorSetFutureAndAbortTransaction(message, ex, fut)
        } catch (ex: AuthorizationException) {
            logErrorAndSetFuture("Kafka producer clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                    "for topic ${publisherConfig.topic} failed to send. Producer is not authorized to write to this topic." +
                    "Closing producer", ex, fut, true)
        } catch (ex: AuthenticationException) {
            logErrorAndSetFuture("Kafka producer clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                    "for topic ${publisherConfig.topic} failed to send. Authentication Failed. Closing producer.", ex, fut, true)
        } catch (ex: InterruptException) {
            val message ="Kafka producer clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                    "for topic ${publisherConfig.topic} failed to send. Thread interrupted."
            logErrorSetFutureAndAbortTransaction(message, ex, fut)
        } catch (ex: TimeoutException) {
            val message = "Kafka producer clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                    "for topic ${publisherConfig.topic} failed to send. Timeout"
            logErrorSetFutureAndAbortTransaction(message, ex, fut)
        } catch (ex: SerializationException) {
            logErrorAndSetFuture("Kafka producer clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                    "for topic ${publisherConfig.topic} failed to send. " +
                    "Key or value are not valid objects given the configured serializers. Closing producer.", ex, fut, true)
        } catch (ex: KafkaException) {
            val message = "Kafka producer clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                    "for topic ${publisherConfig.topic} failed to send. Unknown error. Closing producer."
            logErrorAndSetFuture(message, ex, fut, true)
        }

        return fut
    }

    /**
     * Helper function to set a [future] result based on the presence of an [exception]
     */
    private fun setFutureFromResponse(exception: Exception?, future: OpenFuture<Boolean>) {
        if (exception == null) {
            future.set(true)
        } else {
            future.setException(exception)
        }
    }

    /**
     * Log the [message] and [exception]. Set the [exception] to the [future].
     * If [fatal] is set to true then the producer is closed safely.
     */
    private fun logErrorAndSetFuture(message: String, exception: Exception, future: OpenFuture<Boolean>, fatal: Boolean) {
        log.error(message, exception, future)
        if (fatal) {
            future.setException(CordaMessageAPIFatalException(message, exception))
            safeClose()
        } else {
            future.setException(CordaMessageAPIIntermittentException(message, exception))
        }
    }

    /**
     * Log the [message] and [exception]. Set the [exception] to the [future].
     * If the error occurred as part of a transaction then abort the transaction to reinitialise the producer.
     */
    private fun logErrorSetFutureAndAbortTransaction(message: String, exception: Exception, future: OpenFuture<Boolean>) {
        if (publisherConfig.instanceId != null) {
            producer.abortTransaction()
            logErrorAndSetFuture("$message Aborting transaction and reinitialising producer.", exception, future, false)
        } else {
            logErrorAndSetFuture(message, exception, future, false)
        }
    }

    override fun safeClose() {
        producer.close(Duration.ofMillis(closeTimeout))
    }
}
