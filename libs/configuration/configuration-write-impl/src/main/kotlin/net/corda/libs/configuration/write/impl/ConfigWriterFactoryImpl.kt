package net.corda.libs.configuration.write.impl

import net.corda.config.schema.Schema.Companion.CONFIG_MGMT_REQUEST_TOPIC
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.write.ConfigWriter
import net.corda.libs.configuration.write.ConfigWriterException
import net.corda.libs.configuration.write.ConfigWriterFactory
import net.corda.libs.configuration.write.impl.dbutils.DBUtilsImpl
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.orm.EntityManagerFactoryFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/** An implementation of [ConfigWriterFactory]. */
@Suppress("Unused")
@Component(service = [ConfigWriterFactory::class])
internal class ConfigWriterFactoryImpl @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = LiquibaseSchemaMigrator::class)
    private val schemaMigrator: LiquibaseSchemaMigrator,
    @Reference(service = EntityManagerFactoryFactory::class)
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory
) : ConfigWriterFactory {

    override fun create(config: SmartConfig, instanceId: Int): ConfigWriter {
        val publisher = createPublisher(config, instanceId)
        val subscription = createRPCSubscription(config, instanceId, publisher)
        return ConfigWriterImpl(subscription, publisher)
    }

    /**
     * Creates a [Publisher] using the provided [config] and [instanceId].
     *
     * @throws ConfigWriterException If the publisher cannot be set up.
     */
    private fun createPublisher(config: SmartConfig, instanceId: Int): Publisher {
        val publisherConfig = PublisherConfig(CLIENT_NAME_DB, instanceId)
        return try {
            publisherFactory.createPublisher(publisherConfig, config)
        } catch (e: Exception) {
            throw ConfigWriterException("Could not create publisher to publish updated configuration.", e)
        }
    }

    /**
     * Creates a [ConfigurationManagementRPCSubscription] using the provided [config] and [instanceId]. The
     * subscription is for the [CONFIG_MGMT_REQUEST_TOPIC] topic, and handles requests using a [ConfigWriterProcessor].
     *
     * @throws ConfigWriterException If the subscription cannot be set up.
     */
    private fun createRPCSubscription(
        config: SmartConfig,
        instanceId: Int,
        publisher: Publisher
    ): ConfigurationManagementRPCSubscription {

        val rpcConfig = RPCConfig(
            GROUP_NAME,
            CLIENT_NAME_RPC,
            CONFIG_MGMT_REQUEST_TOPIC,
            ConfigurationManagementRequest::class.java,
            ConfigurationManagementResponse::class.java,
            instanceId
        )
        val dbUtils = DBUtilsImpl(config, schemaMigrator, entityManagerFactoryFactory)
        val processor = ConfigWriterProcessor(publisher, dbUtils)

        return try {
            subscriptionFactory.createRPCSubscription(rpcConfig, config, processor)
        } catch (e: Exception) {
            throw ConfigWriterException("Could not create subscription to process configuration update requests.", e)
        }
    }
}