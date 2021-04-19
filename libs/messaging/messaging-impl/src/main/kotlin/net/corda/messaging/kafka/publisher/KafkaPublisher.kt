package net.corda.messaging.kafka.publisher

import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.messaging.kafka.publisher.builder.PublisherBuilder
import net.corda.messaging.kafka.utils.toProducerRecord
import net.corda.v5.base.concurrent.CordaFuture
import net.corda.v5.base.internal.concurrent.openFuture
import net.corda.v5.base.internal.uncheckedCast
import org.apache.kafka.clients.producer.Producer

/**
 * Kafka publisher will create a new KafkaProducer instance for each publish.
 * Records are sent transactionally. Order is guaranteed. Producer will automatically attempt resends using exactly once semantics
 * to ensure no more than 1 message is delivered.
 */
class KafkaPublisher<K, V>(
    private val clientId: String,
    private val topic: String,
    private val instanceId: Int,
    private val publisherBuilder: PublisherBuilder,
    private val properties: Map<String, String>) : Publisher<K, V> {

    private lateinit var producer: Producer<K, V>

    override fun publish(record: Record<K, V>): CordaFuture<Boolean> {
        producer = publisherBuilder.createPublisher(clientId, instanceId, topic, properties)
        producer.initTransactions()

        producer.beginTransaction()
        val fut = openFuture<Boolean>()
        producer.send(record.toProducerRecord()) { it, ex ->
            if (ex == null) {
                fut.set(true)
            } else {
                fut.setException(ex)
            }
        }
        producer.commitTransaction()
        producer.close()
        return uncheckedCast(fut)
    }
}


