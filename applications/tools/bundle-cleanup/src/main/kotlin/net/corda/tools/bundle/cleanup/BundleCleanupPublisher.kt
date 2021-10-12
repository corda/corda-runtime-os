package net.corda.tools.bundle.cleanup

import com.typesafe.config.ConfigFactory
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [BundleCleanupPublisher::class])
class BundleCleanupPublisher @Activate constructor(
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) {
    companion object {
        private const val KAFKA_BOOTSTRAP_SERVERS_KEY = "messaging.kafka.common.bootstrap.servers"
        private const val KAFKA_TOPIC_PREFIX_KEY = "messaging.topic.prefix"
        private const val KAFKA_TOPIC_PREFIX_VALUE = "bundle-cleanup-"
        private const val KAFKA_TOPIC = "bundle-cleanup-topic"
        private const val KAFKA_CLIENT_ID = "bundle-cleanup-client" // TODO - Can you just use a single client ID for all instances?
    }

    private val publisher: Publisher

    init {
        val config = ConfigFactory.parseMap(mapOf(
            KAFKA_BOOTSTRAP_SERVERS_KEY to "localhost:9093", // TODO - Stop hardcoding this.
            KAFKA_TOPIC_PREFIX_KEY to KAFKA_TOPIC_PREFIX_VALUE
        ))
        publisher = publisherFactory.createPublisher(PublisherConfig(KAFKA_CLIENT_ID), config)
    }

    fun start() {
        // TODO - Check its ok to auto-create the topics here, leave comment to that effect.
        publisher.publish(listOf(Record(KAFKA_TOPIC, "z", "1")))
        publisher.publish(listOf(Record(KAFKA_TOPIC, "zz", "2")))
    }
}