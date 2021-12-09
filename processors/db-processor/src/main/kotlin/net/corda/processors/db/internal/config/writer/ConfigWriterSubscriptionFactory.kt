package net.corda.processors.db.internal.config.writer

import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
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

@Component(service = [ConfigWriterSubscriptionFactory::class])
class ConfigWriterSubscriptionFactory @Activate constructor(
    @Reference(service = DBWriter::class)
    private val dbWriter: DBWriter,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) {
    private companion object {
        private val rpcConfigTemplate = let {
            val topic = CONFIG_UPDATE_REQUEST_TOPIC
            // TODO - Joel - Define own Avro objects, instead of reusing these existing ones.
            val reqClass = PermissionManagementRequest::class.java
            val respClass = PermissionManagementResponse::class.java
            RPCConfig(GROUP_NAME, CLIENT_NAME, topic, reqClass, respClass, null)
        }
    }

    internal fun create(config: SmartConfig, instanceId: Int): Lifecycle {
        val publisherConfig = PublisherConfig(CLIENT_ID, instanceId)
        val publisher = publisherFactory.createPublisher(publisherConfig, config).apply { start() }

        val rpcConfig = rpcConfigTemplate.copy(instanceId = instanceId)
        val processor = ConfigWriterProcessor(dbWriter, publisher)
        return subscriptionFactory.createRPCSubscription(rpcConfig, config, processor).apply { start() }
    }
}