package net.corda.tools.bundle.cleanup

import com.typesafe.config.ConfigFactory
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [DemoPublisher::class])
class DemoPublisher @Activate constructor(
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) {
    private val publisher: Publisher

    init {
        val publisherConfig = PublisherConfig(KAFKA_CLIENT_ID)
        val nodeConfig = ConfigFactory.parseMap(
            mapOf(KAFKA_BOOTSTRAP_SERVERS_KEY to KAFKA_BOOTSTRAP_SERVERS, KAFKA_TOPIC_PREFIX_KEY to KAFKA_TOPIC_PREFIX)
        )
        publisher = publisherFactory.createPublisher(publisherConfig, nodeConfig)
    }

    internal fun publish(key: String, value: String) = publisher.publish(listOf(Record(KAFKA_TOPIC_SUFFIX, key, value)))
}