package net.corda.libs.configuration.write.persistent.impl

import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.write.persistent.PersistentConfigWriter
import net.corda.libs.configuration.write.persistent.PersistentConfigWriterFactory
import net.corda.libs.configuration.write.persistent.TOPIC_CONFIG_MGMT_REQUEST
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/** An implementation of [PersistentConfigWriterFactory]. */
@Suppress("Unused")
@Component(service = [PersistentConfigWriterFactory::class])
class PersistentConfigWriterFactoryImpl @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = DBUtils::class)
    private val dbUtils: DBUtils
) : PersistentConfigWriterFactory {

    override fun create(config: SmartConfig, instanceId: Int): PersistentConfigWriter {
        // TODO - Joel - Hoist this check into the service.
        dbUtils.checkClusterDatabaseConnection(config)
        // This is temporary. In the future, migrations will be applied by a different codepath.
        dbUtils.migrateClusterDatabase(config)

        val publisher = let {
            val publisherConfig = PublisherConfig(CLIENT_NAME_DB, instanceId)
            publisherFactory.createPublisher(publisherConfig, config)
        }

        val subscription = let {
            val reqClass = ConfigurationManagementRequest::class.java
            val respClass = ConfigurationManagementResponse::class.java
            val rpcConfig =
                RPCConfig(GROUP_NAME, CLIENT_NAME_RPC, TOPIC_CONFIG_MGMT_REQUEST, reqClass, respClass, instanceId)

            // TODO - Joel - Don't do anything blocking in processor.
            val processor = PersistentConfigWriterProcessor(config, publisher, dbUtils)

            subscriptionFactory.createRPCSubscription(rpcConfig, config, processor)
        }

        return PersistentConfigWriterImpl(subscription, publisher)
    }
}