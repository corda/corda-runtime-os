package net.corda.messaging.kafka.publisher

import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.messaging.kafka.producer.builder.ProducerBuilder
import net.corda.messaging.kafka.utils.toProducerRecord
import net.corda.v5.base.concurrent.CordaFuture
import net.corda.v5.base.internal.concurrent.openFuture
import org.apache.kafka.clients.producer.Producer

/**
 * Kafka publisher will create a new KafkaProducer instance for each publish.
 * Records are sent transactionally. Order is guaranteed. Producer will automatically attempt resends using exactly once semantics
 * to ensure no more than 1 message is delivered.
 */
class KafkaPublisher<K, V>(
    clientId: String,
    topic: String,
    instanceId: Int,
    producerBuilder: ProducerBuilder<K, V>,
    properties: Map<String, String>) : Publisher<K, V> {

    private var producer: Producer<K, V> = producerBuilder.createProducer(clientId, instanceId, topic, properties)

    init {
        producer.initTransactions()
    }

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
        }
        return fut
    }
}




