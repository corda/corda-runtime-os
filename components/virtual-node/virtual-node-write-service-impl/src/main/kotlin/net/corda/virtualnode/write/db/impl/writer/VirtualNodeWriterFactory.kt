package net.corda.virtualnode.write.db.impl.writer

import net.corda.data.virtualnode.VirtualNodeCreationRequest
import net.corda.data.virtualnode.VirtualNodeCreationResponse
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.connection.manager.DbAdmin
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.VirtualNode.Companion.VIRTUAL_NODE_CREATION_REQUEST_TOPIC

/** A factory for [VirtualNodeWriter]s. */
internal class VirtualNodeWriterFactory(
    private val subscriptionFactory: SubscriptionFactory,
    private val publisherFactory: PublisherFactory,
    private val dbConnectionManager: DbConnectionManager,
    private val dbAdmin: DbAdmin,
    private val schemaMigrator: LiquibaseSchemaMigrator
) {

    /**
     * Creates a [VirtualNodeWriter].
     *
     * @param config Config to use for subscribing to Kafka.
     * @param instanceId The instance ID to use for subscribing to Kafka.
     *
     * @throws `CordaMessageAPIException` If the publisher cannot be set up.
     */
    fun create(config: SmartConfig, instanceId: Int): VirtualNodeWriter {
        val vnodePublisher = createPublisher(config, instanceId)
        val subscription = createRPCSubscription(config, vnodePublisher)
        return VirtualNodeWriter(subscription, vnodePublisher)
    }

    /**
     * Creates a [Publisher] using the provided [config] and [instanceId].
     *
     * @throws `CordaMessageAPIException` If the publisher cannot be set up.
     */
    private fun createPublisher(config: SmartConfig, instanceId: Int): Publisher {
        val publisherConfig = PublisherConfig(CLIENT_NAME_DB, instanceId)
        return publisherFactory.createPublisher(publisherConfig, config)
    }

    /**
     * Creates a [RPCSubscription]<VirtualNodeCreationRequest, VirtualNodeCreationResponse> using the provided
     * [config]. The subscription is to the [VIRTUAL_NODE_CREATION_REQUEST_TOPIC] topic, and handles requests using a
     * [VirtualNodeWriterProcessor].
     */
    private fun createRPCSubscription(
        config: SmartConfig,
        vnodePublisher: Publisher,
    ): RPCSubscription<VirtualNodeCreationRequest, VirtualNodeCreationResponse> {

        val rpcConfig = RPCConfig(
            GROUP_NAME,
            CLIENT_NAME_RPC,
            VIRTUAL_NODE_CREATION_REQUEST_TOPIC,
            VirtualNodeCreationRequest::class.java,
            VirtualNodeCreationResponse::class.java,
        )
        val virtualNodeEntityRepository = VirtualNodeEntityRepository(dbConnectionManager)
        val vnodeDbFactory = VirtualNodeDbFactory(dbConnectionManager, dbAdmin, schemaMigrator)
        val processor = VirtualNodeWriterProcessor(vnodePublisher, dbConnectionManager, virtualNodeEntityRepository, vnodeDbFactory)

        return subscriptionFactory.createRPCSubscription(rpcConfig, config, processor)
    }
}