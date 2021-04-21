package net.corda.messaging.kafka.publisher

import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.messaging.kafka.producer.builder.ProducerBuilder
import net.corda.messaging.kafka.utils.toProducerRecord
import net.corda.v5.base.concurrent.CordaFuture
import net.corda.v5.base.internal.concurrent.openFuture
import org.apache.kafka.clients.producer.Producer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Kafka publisher will create a new KafkaProducer instance of KafkaPublisher.
 * Records are sent transactionally. Order is guaranteed. Producer will automatically attempt resends using exactly once semantics
 * to ensure no more than 1 message is delivered.
 */
class KafkaPublisher<K, V>(
    private val clientId: String,
    private val topic: String,
    private val instanceId: Int,
    producerBuilder: ProducerBuilder<K, V>,
    properties: Map<String, String>) : Publisher<K, V> {

    private var producer: Producer<K, V> = producerBuilder.createProducer(clientId, instanceId, topic, properties)

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
            log.error("Kafka producer clientId $clientId, instanceId $instanceId, for topic $topic failed to send. Closing producer", ex)
        }

        return fut
    }
}




