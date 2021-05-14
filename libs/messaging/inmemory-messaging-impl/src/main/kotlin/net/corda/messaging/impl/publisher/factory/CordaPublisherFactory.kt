package net.corda.messaging.impl.publisher.factory

import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.impl.publisher.CordaPublisher
import net.corda.messaging.impl.subscription.subscriptions.pubsub.service.TopicService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component
class CordaPublisherFactory @Activate constructor(
    @Reference(service = TopicService::class)
    private val topicService: TopicService) : PublisherFactory {

    override fun <K : Any, V : Any> createPublisher(
        publisherConfig: PublisherConfig,
        properties: Map<String, String>,
        keyClass: Class<K>,
        valueClass: Class<V>
    ): Publisher<K, V> {
        return CordaPublisher(publisherConfig, topicService)
    }
}
