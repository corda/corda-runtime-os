package net.corda.libs.configuration.write.persistent.impl

import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.write.persistent.PersistentConfigWriter
import net.corda.libs.configuration.write.persistent.PersistentConfigWriterException
import net.corda.libs.configuration.write.persistent.PersistentConfigWriterFactory
import net.corda.libs.configuration.write.persistent.TOPIC_CONFIG_MGMT_REQUEST
import net.corda.messaging.api.publisher.Publisher
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
        // This is temporary. In the future, migrations will be applied by a different codepath.
        dbUtils.migrateClusterDatabase(config)

        val publisher = createPublisher(config, instanceId)
        val subscription = createRPCSubscription(config, instanceId, publisher)
        return PersistentConfigWriterImpl(subscription, publisher)
    }

    /**
     * Creates a [Publisher] using the provided [config] and [instanceId].
     *
     * @throws PersistentConfigWriterException If the publisher cannot be set up.
     */
    private fun createPublisher(config: SmartConfig, instanceId: Int): Publisher {
        val publisherConfig = PublisherConfig(CLIENT_NAME_DB, instanceId)
        return try {
            publisherFactory.createPublisher(publisherConfig, config)
        } catch (e: Exception) {
            throw PersistentConfigWriterException("Could not create `PersistentConfigWriter`.", e)
        }
    }

    /**
     * Creates a [ConfigMgmtRPCSubscription] using the provided [config] and [instanceId]. The subscription
     * is for the [TOPIC_CONFIG_MGMT_REQUEST] topic, and handles requests using a [PersistentConfigWriterProcessor].
     *
     * @throws PersistentConfigWriterException If the subscription cannot be set up.
     */
    private fun createRPCSubscription(
        config: SmartConfig,
        instanceId: Int,
        publisher: Publisher
    ): ConfigMgmtRPCSubscription {
        val rpcConfig = RPCConfig(
            GROUP_NAME,
            CLIENT_NAME_RPC,
            TOPIC_CONFIG_MGMT_REQUEST,
            ConfigurationManagementRequest::class.java,
            ConfigurationManagementResponse::class.java,
            instanceId
        )

        // TODO - Joel - Don't do anything blocking in processor. Raise separate JIRA.
        val processor = PersistentConfigWriterProcessor(publisher, config, dbUtils)
        return try {
            subscriptionFactory.createRPCSubscription(rpcConfig, config, processor)
        } catch (e: Exception) {
            throw PersistentConfigWriterException("Could not create `PersistentConfigWriter`.", e)
        }
    }
}