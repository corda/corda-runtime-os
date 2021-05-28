package net.corda.messaging.kafka.subscription.producer.builder

import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.kafka.subscription.producer.wrapper.CordaKafkaProducer
import org.apache.kafka.clients.consumer.Consumer

/**
 * Builder for creating producers for use in Subscriptions. Records received from processors are written back to kafka using this.
 */
interface SubscriptionProducerBuilder {

    /**
     * Generate a Corda Kafka Producer.
     * @return CordaKafkaProducer
     * @throws CordaMessageAPIFatalException if fatal error occurs during construction of the producer
     * @throws CordaMessageAPIIntermittentException if error occurs during construction of the producer that can be retried
     */
    fun createProducer(consumer: Consumer<*, *>) : CordaKafkaProducer
}
