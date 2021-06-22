package net.corda.messaging.emulation.publisher.factory

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.emulation.publisher.CordaPublisher
import net.corda.messaging.emulation.topic.service.TopicService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * In-memory implementation for Publisher Factory.
 * @property topicService OSGi DS Injected topic service
 */
@Component
class CordaPublisherFactory @Activate constructor(
    @Reference(service = TopicService::class)
    private val topicService: TopicService
) : PublisherFactory {

    companion object {
        const val PUBLISHER_INSTANCE_ID = "instanceId"
        const val PUBLISHER_CLIENT_ID = "clientId"
    }

    override fun createPublisher(
        publisherConfig: PublisherConfig,
        nodeConfig: Config
    ): Publisher {
        var config = nodeConfig
            .withFallback(ConfigFactory.load("tmpInMemDefaults"))
            .withValue(PUBLISHER_CLIENT_ID, ConfigValueFactory.fromAnyRef(publisherConfig.clientId))

        val instanceId = publisherConfig.instanceId
        if (instanceId != null) {
            config = config.withValue(PUBLISHER_INSTANCE_ID, ConfigValueFactory.fromAnyRef(instanceId))
        }
        return CordaPublisher(config, topicService)
    }
}
