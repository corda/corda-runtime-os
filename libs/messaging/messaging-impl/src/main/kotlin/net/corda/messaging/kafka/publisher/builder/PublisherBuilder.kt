package net.corda.messaging.kafka.publisher.builder

import org.apache.kafka.clients.producer.Producer

/**
 * Publisher Builder Interface for creating Producers.
 */
interface PublisherBuilder {

    /**
    * Generate publisher with given properties.
    * @param clientId id of the publisher sent to the server. Used as part of server side request logging.
    * @param topic topic this publisher is intended for.
    * @param instanceId unique id of this publisher instance. Used as part of the transactions.
    * It is used serverside to identify the same producer instance across process restarts as part of exactly once delivery.
    * @return Producer capable of publishing records to a topic.
    */
    fun <K, V> createPublisher(
        clientId: String,
        instanceId: Int,
        topic: String,
        properties: Map<String, String>
    ): Producer<K, V>
}
