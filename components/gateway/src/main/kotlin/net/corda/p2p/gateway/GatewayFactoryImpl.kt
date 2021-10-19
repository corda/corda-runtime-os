package net.corda.p2p.gateway

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component
class GatewayFactoryImpl @Activate constructor(
    @Reference(service = ConfigurationReadService::class)
    private val configurationReaderService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
) : GatewayFactory {
    override fun createGateway(nodeConfiguration: Config): Gateway {
        return GatewayImpl(
            configurationReaderService,
            subscriptionFactory,
            publisherFactory,
            lifecycleCoordinatorFactory,
            nodeConfiguration
        )
    }
}
