package net.cordax.flowworker.api.publisher.factory

import net.cordax.flowworker.api.publisher.Publisher


interface PublisherFactory {

    fun <K, V> createPublisher(
        groupName: String,
        instanceId: Int,
        topic: String,
        properties: Map<String, String>
    ): Publisher<K, V>
}