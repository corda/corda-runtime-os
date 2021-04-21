package net.corda.messaging.kafka.publisher

import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.kafka.producer.builder.ProducerBuilder
import net.corda.messaging.kafka.utils.toProducerRecord
import net.corda.v5.base.concurrent.CordaFuture
import net.corda.v5.base.internal.concurrent.openFuture
import org.apache.kafka.clients.producer.Producer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Kafka publisher will create a new KafkaProducer instance of KafkaPublisher.
 * Records are sent transactionally. Order is guaranteed. Producer will automatically attempt resends using exactly once semantics
 * to ensure no more than 1 message is delivered.
 */
class KafkaPublisher<K, V>(
    private val publisherConfig: PublisherConfig,
    producerProperties: Properties,
    producerBuilder: ProducerBuilder<K, V>) : Publisher<K, V> {

    private var producer: Producer<K, V> = producerBuilder.createProducer(producerProperties)

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    init {
        producer.initTransactions()
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
                    fut.setException(ex)
                }
            }
            producer.commitTransaction()
        } catch (ex : Exception) {
            producer.close()
            log.error("Kafka producer clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                    "for topic ${publisherConfig.topic} failed to send. Closing producer", ex)
        }

        return fut
    }
}




