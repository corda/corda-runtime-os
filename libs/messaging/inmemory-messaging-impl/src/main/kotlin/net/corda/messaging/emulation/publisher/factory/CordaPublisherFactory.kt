package net.corda.messaging.emulation.publisher.factory

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.emulation.publisher.CordaPublisher
import net.corda.messaging.emulation.publisher.RPCSenderImpl
import net.corda.messaging.emulation.rpc.RPCTopicService
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
    private val topicService: TopicService,
    @Reference(service = RPCTopicService::class)
    private val rpcTopicService: RPCTopicService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
) : PublisherFactory {

    companion object {
        const val PUBLISHER_INSTANCE_ID = "instanceId"
        const val PUBLISHER_CLIENT_ID = "clientId"
    }

    override fun createPublisher(
        publisherConfig: PublisherConfig,
        kafkaConfig: SmartConfig
    ): Publisher {
        var config = kafkaConfig
            .withFallback(ConfigFactory.load("tmpInMemDefaults"))
            .withValue(PUBLISHER_CLIENT_ID, ConfigValueFactory.fromAnyRef(publisherConfig.clientId))

        val instanceId = publisherConfig.instanceId
        if (instanceId != null) {
            config = config.withValue(PUBLISHER_INSTANCE_ID, ConfigValueFactory.fromAnyRef(instanceId))
        }
        return CordaPublisher(config, topicService)
    }

    override fun <REQUEST : Any, RESPONSE : Any> createRPCSender(
        rpcConfig: RPCConfig<REQUEST, RESPONSE>,
        kafkaConfig: SmartConfig
    ): RPCSender<REQUEST, RESPONSE> {
        return RPCSenderImpl(rpcConfig, rpcTopicService, lifecycleCoordinatorFactory)
    }
}
