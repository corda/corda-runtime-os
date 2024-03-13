package net.corda.messaging.emulation.publisher.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.publisher.HttpRpcClient
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.emulation.http.HttpService
import net.corda.messaging.emulation.publisher.CordaPublisher
import net.corda.messaging.emulation.publisher.HttpRpcClientImpl
import net.corda.messaging.emulation.publisher.RPCSenderImpl
import net.corda.messaging.emulation.rpc.RPCTopicService
import net.corda.messaging.emulation.topic.service.TopicService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory implementation for Publisher Factory.
 * @property topicService OSGi DS Injected topic service
 */
@Component
internal class CordaPublisherFactory @Activate constructor(
    @Reference(service = TopicService::class)
    private val topicService: TopicService,
    @Reference(service = RPCTopicService::class)
    private val rpcTopicService: RPCTopicService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = HttpService::class)
    private val httpService: HttpService,
) : PublisherFactory {
    private companion object {
        val instanceIndex = AtomicInteger()
    }

    override fun createPublisher(
        publisherConfig: PublisherConfig,
        messagingConfig: SmartConfig
    ): Publisher {
        return CordaPublisher(publisherConfig, topicService)
    }

    override fun <REQUEST : Any, RESPONSE : Any> createRPCSender(
        rpcConfig: RPCConfig<REQUEST, RESPONSE>,
        messagingConfig: SmartConfig
    ): RPCSender<REQUEST, RESPONSE> {
        return RPCSenderImpl(
            rpcConfig,
            rpcTopicService,
            lifecycleCoordinatorFactory,
            instanceIndex.incrementAndGet().toString(),
        )
    }

    override fun createHttpRpcClient(): HttpRpcClient {
        return HttpRpcClientImpl(httpService)
    }
}
