package net.corda.virtualnode.write.db.impl.writer

import net.corda.data.virtualnode.VirtualNodeAsynchronousRequest
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
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.VirtualNode.Companion.VIRTUAL_NODE_ASYNC_REQUEST_TOPIC
import net.corda.schema.Schemas.VirtualNode.Companion.VIRTUAL_NODE_CREATION_REQUEST_TOPIC
import net.corda.utilities.time.UTCClock
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.VirtualNodeAsyncOperationProcessor
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.handlers.VirtualNodeUpgradeOperationHandler
import javax.persistence.EntityManager
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.utility.MigrationUtilityImpl

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

    private companion object {
        const val ASYNC_OPERATION_GROUP = "virtual.node.async.operation.group"
    }

    /**
     * Creates a [VirtualNodeWriter].
     *
     * @param messagingConfig Config to use for subscribing to Kafka.
     *
     * @throws `CordaMessageAPIException` If the publisher cannot be set up.
     */
    fun create(messagingConfig: SmartConfig): VirtualNodeWriter {
        val virtualNodeInfoPublisher = createPublisher(messagingConfig)
        val rpcSubscription = createRPCSubscription(messagingConfig, virtualNodeInfoPublisher)
        val asyncOperationSubscription = createAsyncOperationSubscription(messagingConfig, virtualNodeInfoPublisher)
        return VirtualNodeWriter(rpcSubscription, asyncOperationSubscription, virtualNodeInfoPublisher)
    }

    /**
     * Create a subscription for handling asynchronous virtual node operations.
     */
    private fun createAsyncOperationSubscription(
        messagingConfig: SmartConfig,
        virtualNodeInfoPublisher: Publisher,
    ): Subscription<String, VirtualNodeAsynchronousRequest> {
        val subscriptionConfig = SubscriptionConfig(ASYNC_OPERATION_GROUP, VIRTUAL_NODE_ASYNC_REQUEST_TOPIC)
        val oldVirtualNodeEntityRepository = VirtualNodeEntityRepository(this.dbConnectionManager.getClusterEntityManagerFactory())
        val migrationUtility = MigrationUtilityImpl(dbConnectionManager, LiquibaseSchemaMigratorImpl())

        val virtualNodeUpgradeHandler = VirtualNodeUpgradeOperationHandler(
            this.dbConnectionManager.getClusterEntityManagerFactory(),
            oldVirtualNodeEntityRepository,
            virtualNodeInfoPublisher,
            migrationUtility
        )
        val asyncOperationProcessor = VirtualNodeAsyncOperationProcessor(virtualNodeUpgradeHandler)

        return subscriptionFactory.createDurableSubscription(
            subscriptionConfig, asyncOperationProcessor, messagingConfig, null
        )
    }

    /**
     * Creates a [Publisher] using the provided [config].
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
