package net.corda.messaging.kafka.publisher

import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.kafka.utils.toProducerRecord
import net.corda.v5.base.concurrent.CordaFuture
import net.corda.v5.base.internal.concurrent.openFuture
import org.apache.kafka.clients.producer.Producer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Kafka publisher will create a new KafkaProducer instance of KafkaPublisher.
 * Records are sent via transactions. Order is guaranteed. Producer will automatically attempt resends using exactly once semantics
 * to ensure no more than 1 message is delivered based on kafka configuration.
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

        try {
            producer.beginTransaction()

            producer.send(record.toProducerRecord()) { it, ex ->
                if (ex == null) {
                    fut.set(true)
                } else {
                    fut.set(false)
                    fut.setException(ex)
                }
            }
            producer.commitTransaction()
        } catch (ex : Exception) {
            log.error("Kafka producer clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                    "for topic ${publisherConfig.topic} failed to send. Closing producer", ex)
            fut.set(false)
            fut.setException(ex)
            //should we close here? perhaps we should add API call to close the Publisher?
        }

        return fut
    }
}




