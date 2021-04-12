package net.corda.messaging.api.publisher.factory

import net.corda.messaging.api.publisher.Publisher


interface PublisherFactory {

    fun <K, V> createPublisher(
        clientId: String,
        topic: String,
        properties: Map<String, String>
    ): Publisher<K, V>
}