package net.corda.processors.db.internal.config.writer

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.processors.db.internal.db.DBWriter
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * An implementation of [ConfigWriterSubscriptionFactory] that creates an `RPCSubscription` for processing new config
 * requests and a `Publisher` for publishing the updated config for use by the rest of the cluster.
 *
 * Processing is delegated to [ConfigWriterProcessor].
 */
@Suppress("Unused")
@Component(service = [ConfigWriterSubscriptionFactory::class])
class ConfigWriterSubscriptionFactoryImpl @Activate constructor(
    @Reference(service = DBWriter::class)
    private val dbWriter: DBWriter,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : ConfigWriterSubscriptionFactory {

    override fun create(config: SmartConfig, instanceId: Int): Lifecycle {
        val publisherConfig = PublisherConfig(CLIENT_NAME_DB, instanceId)
        val rpcConfig =
            RPCConfig(GROUP_NAME, CLIENT_NAME_RPC, TOPIC_CONFIG_UPDATE_REQUEST, REQ_CLASS, RESP_CLASS, instanceId)

        val publisher = publisherFactory.createPublisher(publisherConfig, config)
        val processor = ConfigWriterProcessor(dbWriter, publisher)
        val subscription = subscriptionFactory.createRPCSubscription(rpcConfig, config, processor)
        
        publisher.start()
        // TODO - Joel - Allow publisher to be closed.
        subscription.start()

        return subscription
    }
}