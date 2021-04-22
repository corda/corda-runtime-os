package net.corda.messaging.kafka.publisher

import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.kafka.utils.toProducerRecord
import net.corda.v5.base.concurrent.CordaFuture
import net.corda.v5.base.internal.concurrent.OpenFuture
import net.corda.v5.base.internal.concurrent.openFuture
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.errors.AuthenticationException
import org.apache.kafka.common.errors.AuthorizationException
import org.apache.kafka.common.errors.InterruptException
import org.apache.kafka.common.errors.SerializationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Kafka publisher will create a new KafkaProducer instance of KafkaPublisher.
 * Records are sent via transactions if [publisherConfig].instanceId is not null.
 * Producer will automatically attempt resends. If instanceId is set resends will have exactly once semantics.
 * to ensure no more than 1 message is delivered.
 * Any Exceptions thrown as part of the transaction are returned in a CordaFuture.
 */
class KafkaPublisher<K, V>(
    private val publisherConfig: PublisherConfig,
    private val producer: Producer<K, V>) : Publisher<K, V> {

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    @Suppress("TooGenericExceptionCaught")
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
        } catch (ex: AuthenticationException) {
            logErrorAndSetFuture("Kafka producer clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                    "for topic ${publisherConfig.topic} failed to send. Authentication Failed.", ex, fut)
        } catch (ex: AuthorizationException) {
            logErrorAndSetFuture("Kafka producer clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                    "for topic ${publisherConfig.topic} failed to send. Producer is not authorized to write to this topic.", ex, fut)
        } catch (ex: IllegalStateException) {
            logErrorAndSetFuture("Kafka producer clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                    "for topic ${publisherConfig.topic} failed to send. Producer is in an illegal state.", ex, fut)
        } catch (ex: InterruptException) {
            logErrorAndSetFuture("Kafka producer clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                    "for topic ${publisherConfig.topic} failed to send. Thread interrupted", ex, fut)

        } catch (ex: SerializationException) {
            logErrorAndSetFuture("Kafka producer clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                    "for topic ${publisherConfig.topic} failed to send. " +
                    "Key or value are not valid objects given the configured serializers", ex, fut)
        } catch (ex: KafkaException) {
            logErrorAndSetFuture("Kafka producer clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                    "for topic ${publisherConfig.topic} failed to send.", ex, fut)
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
            future.set(false)
            future.setException(exception)
        }
    }

    /**
     * Log the [message] and [exception]. Set the [exception] to the [future].
     */
    private fun logErrorAndSetFuture(message: String, exception: Exception, future: OpenFuture<Boolean>) {
        log.error(message, exception, future)
        future.set(false)
        future.setException(exception)
    }
}
