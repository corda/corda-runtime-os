package net.corda.messaging.kafka.publisher.builder.impl

import net.corda.messaging.kafka.publisher.builder.PublisherBuilder
import net.corda.messaging.kafka.utils.setKafkaProperties
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import java.util.*

/**
 * Builder a Kafka Publisher.
 */
class KafkaPublisherBuilder : PublisherBuilder {

    override fun <K, V> createPublisher(clientId: String, instanceId: Int, topic: String, properties: Map<String, String>): Producer<K, V> {
        val producerProps = getProducerProperties(clientId, topic, instanceId, properties)
        return KafkaProducer(producerProps)
    }

    /**
     * Generate producer properties with default values unless overridden by the given config values and [properties]
     * @param clientId id of the publisher sent to the server. Used as part of server side request logging.
     * @param topic topic this publisher is created for.
     * @param instanceId unique id of this publisher instance. Used as part of the transactions.
     * It is used serverside to identify the same producer instance across process restarts as part of exactly once delivery.
     * @return Kafka Properties.
     */
    private fun getProducerProperties(clientId: String, topic: String, instanceId: Int, properties: Map<String, String>): Properties {
        //TODO - get config values from a configService which will be initialized on startup from a compacted log topic

        val producerProps = Properties()
        producerProps[ProducerConfig.CLIENT_ID_CONFIG] = clientId
        producerProps[ProducerConfig.TRANSACTIONAL_ID_CONFIG] =
            "publishing-producer-$clientId-$topic-$instanceId"
        setKafkaProperties(producerProps, properties, ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9093")
        setKafkaProperties(
            producerProps,
            properties,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
            StringSerializer::class.java.name
        )
        setKafkaProperties(
            producerProps,
            properties,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
            ByteArraySerializer::class.java.name
        )
        setKafkaProperties(producerProps, properties, ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
        setKafkaProperties(producerProps, properties, ProducerConfig.ACKS_CONFIG, "all")
        return producerProps
    }
}
