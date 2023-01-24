package net.corda.virtualnode.write.db.impl.writer

import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.connection.manager.DbAdmin
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.cpi.datamodel.CpkDbChangeLogEntity
import net.corda.libs.cpi.datamodel.findCurrentCpkChangeLogsForCpi
import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.VirtualNode.Companion.VIRTUAL_NODE_CREATION_REQUEST_TOPIC
import net.corda.utilities.time.UTCClock
import javax.persistence.EntityManager

/** A factory for [VirtualNodeWriter]s. */
@Suppress("LongParameterList")
internal class VirtualNodeWriterFactory(
    private val subscriptionFactory: SubscriptionFactory,
    private val publisherFactory: PublisherFactory,
    private val dbConnectionManager: DbConnectionManager,
    private val dbAdmin: DbAdmin,
    private val schemaMigrator: LiquibaseSchemaMigrator,
    private val groupPolicyParser: GroupPolicyParser,
    private val getCurrentChangeLogsForCpi: (EntityManager, String, String, String) -> List<CpkDbChangeLogEntity> =
        ::findCurrentCpkChangeLogsForCpi
) {

    /**
     * Creates a [VirtualNodeWriter].
     *
     * @param messagingConfig Config to use for subscribing to Kafka.
     *
     * @throws `CordaMessageAPIException` If the publisher cannot be set up.
     */
    fun create(messagingConfig: SmartConfig): VirtualNodeWriter {
        val vnodePublisher = createPublisher(messagingConfig)
        val subscription = createRPCSubscription(messagingConfig, vnodePublisher)
        return VirtualNodeWriter(subscription, vnodePublisher)
    }

    /**
     * Creates a [Publisher] using the provided [config] and [instanceId].
     *
     * @throws `CordaMessageAPIException` If the publisher cannot be set up.
     */
    private fun createPublisher(config: SmartConfig): Publisher {
        val publisherConfig = PublisherConfig(CLIENT_NAME_DB)
        return publisherFactory.createPublisher(publisherConfig, config)
    }

    /**
     * Creates a [RPCSubscription]<VirtualNodeCreationRequest, VirtualNodeCreationResponse> using the provided
     * [messagingConfig]. The subscription is to the [VIRTUAL_NODE_CREATION_REQUEST_TOPIC] topic, and handles requests using a
     * [VirtualNodeWriterProcessor].
     */
    private fun createRPCSubscription(
        messagingConfig: SmartConfig,
        vnodePublisher: Publisher,
    ): RPCSubscription<VirtualNodeManagementRequest, VirtualNodeManagementResponse> {

        val rpcConfig = RPCConfig(
            GROUP_NAME,
            CLIENT_NAME_RPC,
            VIRTUAL_NODE_CREATION_REQUEST_TOPIC,
            VirtualNodeManagementRequest::class.java,
            VirtualNodeManagementResponse::class.java,
        )
        val virtualNodeEntityRepository =
            VirtualNodeEntityRepository(dbConnectionManager.getClusterEntityManagerFactory())
        val vnodeDbFactory = VirtualNodeDbFactory(dbConnectionManager, dbAdmin, schemaMigrator)
        val processor = VirtualNodeWriterProcessor(
            vnodePublisher,
            dbConnectionManager,
            virtualNodeEntityRepository,
            vnodeDbFactory,
            groupPolicyParser,
            UTCClock(),
            getCurrentChangeLogsForCpi
        )

        return subscriptionFactory.createRPCSubscription(rpcConfig, messagingConfig, processor)
    }
}
