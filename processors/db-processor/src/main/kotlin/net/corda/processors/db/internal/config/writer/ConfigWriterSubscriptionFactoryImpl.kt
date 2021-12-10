package net.corda.processors.db.internal.config.writer

import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.io.Closeable

/**
 * An implementation of [ConfigWriterSubscriptionFactory] that creates an `RPCSubscription` for processing new config
 * requests and a `Publisher` for publishing the updated config for use by the rest of the cluster.
 *
 * Processing is delegated to [ConfigWriterProcessor].
 */
@Suppress("Unused")
@Component(service = [ConfigWriterSubscriptionFactory::class])
class ConfigWriterSubscriptionFactoryImpl @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : ConfigWriterSubscriptionFactory {

    override fun create(config: SmartConfig, instanceId: Int, dbUtils: DBUtils): Closeable {
        val publisher = let {
            val publisherConfig = PublisherConfig(CLIENT_NAME_DB, instanceId)
            publisherFactory.createPublisher(publisherConfig, config)
        }

        val rpcConfig = let {
            val requestClass = ConfigurationManagementRequest::class.java
            val responseClass = ConfigurationManagementResponse::class.java
            RPCConfig(GROUP_NAME, CLIENT_NAME_RPC, TOPIC_CONFIG_UPDATE_REQUEST, requestClass, responseClass, instanceId)
        }

        val processor = ConfigWriterProcessor(dbUtils, publisher)
        val subscription = subscriptionFactory.createRPCSubscription(rpcConfig, config, processor)

        publisher.start()
        subscription.start()

        return Closeable {
            subscription.stop()
            publisher.close()
        }
    }
}