package net.corda.virtualnode.write.db.impl.writer

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.virtualnode.VirtualNodeAsynchronousRequest
import net.corda.data.virtualnode.VirtualNodeCreateRequest
import net.corda.data.virtualnode.VirtualNodeDbConnectionUpdateRequest
import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.data.virtualnode.VirtualNodeUpgradeRequest
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.cpi.datamodel.repository.CpkDbChangeLogRepository
import net.corda.libs.cpi.datamodel.repository.factory.CpiCpkRepositoryFactory
import net.corda.libs.external.messaging.ExternalMessagingConfigProviderImpl
import net.corda.libs.external.messaging.ExternalMessagingRouteConfigGeneratorImpl
import net.corda.libs.external.messaging.serialization.ExternalMessagingChannelConfigSerializerImpl
import net.corda.libs.external.messaging.serialization.ExternalMessagingRouteConfigSerializerImpl
import net.corda.libs.virtualnode.datamodel.repository.HoldingIdentityRepositoryImpl
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepository
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepositoryImpl
import net.corda.membership.client.MemberResourceClient
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.schema.Schemas.VirtualNode.VIRTUAL_NODE_ASYNC_REQUEST_TOPIC
import net.corda.schema.Schemas.VirtualNode.VIRTUAL_NODE_CREATION_REQUEST_TOPIC
import net.corda.schema.configuration.VirtualNodeDatasourceConfig
import net.corda.utilities.PathProvider
import net.corda.utilities.TempPathProvider
import net.corda.utilities.time.UTCClock
import net.corda.virtualnode.write.db.impl.VirtualNodesDbAdmin
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.VirtualNodeAsyncOperationHandler
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.VirtualNodeAsyncOperationProcessor
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.factories.RecordFactoryImpl
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.handlers.CreateVirtualNodeOperationHandler
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.handlers.UpdateVirtualNodeDbOperationHandler
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.handlers.VirtualNodeOperationStatusHandler
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.handlers.VirtualNodeSchemaHandler
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.handlers.VirtualNodeUpgradeOperationHandler
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.services.CreateVirtualNodeServiceImpl
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.services.UpdateVirtualNodeServiceImpl
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.utility.MigrationUtilityImpl
import org.slf4j.LoggerFactory

/** A factory for [VirtualNodeWriter]s. */
@Suppress("LongParameterList")
internal class VirtualNodeWriterFactory(
    private val subscriptionFactory: SubscriptionFactory,
    private val publisherFactory: PublisherFactory,
    private val dbConnectionManager: DbConnectionManager,
    private val virtualNodeDbAdmin: VirtualNodesDbAdmin,
    private val schemaMigrator: LiquibaseSchemaMigrator,
    private val groupPolicyParser: GroupPolicyParser,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val memberResourceClient: MemberResourceClient,
    private val membershipQueryClient: MembershipQueryClient,
    private val memberInfoFactory: MemberInfoFactory,
    private val cpiCpkRepositoryFactory: CpiCpkRepositoryFactory,
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    private val cpkDbChangeLogRepository: CpkDbChangeLogRepository = CpiCpkRepositoryFactory().createCpkDbChangeLogRepository(),
    private val tempPathProvider: PathProvider = TempPathProvider(),
) {

    companion object {
        private const val ASYNC_OPERATION_GROUP = "virtual.node.async.operation.group"
        private const val OFFLINE_DB_DIR = "offline-db"
    }

    /**
     * Creates a [VirtualNodeWriter].
     *
     * @param messagingConfig Config to use for subscribing to Kafka.
     *
     * @throws `CordaMessageAPIException` If the publisher cannot be set up.
     */
    fun create(
        messagingConfig: SmartConfig,
        externalMsgConfig: SmartConfig,
        vnodeDatasourceConfig: SmartConfig,
        bootConfig: SmartConfig,
    ): VirtualNodeWriter {
        val publisher = createPublisher(messagingConfig)
        val rpcSubscription = createRPCSubscription(messagingConfig, bootConfig, publisher)
        val asyncOperationSubscription =
            createAsyncOperationSubscription(messagingConfig, externalMsgConfig, vnodeDatasourceConfig, publisher)
        return VirtualNodeWriter(rpcSubscription, asyncOperationSubscription, publisher)
    }

    /**
     * Create a subscription for handling asynchronous virtual node operations.
     */
    private fun createAsyncOperationSubscription(
        messagingConfig: SmartConfig,
        externalMsgConfig: SmartConfig,
        vnodeDatasourceConfig: SmartConfig,
        publisher: Publisher,
    ): Subscription<String, VirtualNodeAsynchronousRequest> {
        val subscriptionConfig = SubscriptionConfig(ASYNC_OPERATION_GROUP, VIRTUAL_NODE_ASYNC_REQUEST_TOPIC)
        val oldVirtualNodeEntityRepository =
            VirtualNodeEntityRepository(
                dbConnectionManager.getClusterEntityManagerFactory(),
                cpiCpkRepositoryFactory.createCpiMetadataRepository()
            )
        val migrationUtility = MigrationUtilityImpl(dbConnectionManager, schemaMigrator)
        val externalMessagingRouteConfigGenerator = ExternalMessagingRouteConfigGeneratorImpl(
            ExternalMessagingConfigProviderImpl(externalMsgConfig),
            ExternalMessagingRouteConfigSerializerImpl(),
            ExternalMessagingChannelConfigSerializerImpl()
        )

        val createVirtualNodeService = CreateVirtualNodeServiceImpl(
            dbConnectionManager,
            cpkDbChangeLogRepository,
            oldVirtualNodeEntityRepository,
            VirtualNodeRepositoryImpl(),
            HoldingIdentityRepositoryImpl(),
            publisher
        )

        val updateVirtualNodeService = UpdateVirtualNodeServiceImpl(
            dbConnectionManager,
            VirtualNodeRepositoryImpl(),
            HoldingIdentityRepositoryImpl(),
            publisher
        )

        val virtualNodesDdlPoolConfig = vnodeDatasourceConfig.getConfig(VirtualNodeDatasourceConfig.VNODE_DDL_POOL_CONFIG)
        val virtualNodesDmlPoolConfig = vnodeDatasourceConfig.getConfig(VirtualNodeDatasourceConfig.VNODE_DML_POOL_CONFIG)

        val virtualNodeDbFactory =
            VirtualNodeDbFactoryImpl(
                dbConnectionManager,
                virtualNodeDbAdmin,
                schemaMigrator,
                virtualNodesDdlPoolConfig,
                virtualNodesDmlPoolConfig
            )

        val recordFactory = RecordFactoryImpl(UTCClock(), memberInfoFactory)

        val handlerMap = mutableMapOf<Class<*>, VirtualNodeAsyncOperationHandler<*>>(
            VirtualNodeUpgradeRequest::class.java to VirtualNodeUpgradeOperationHandler(
                dbConnectionManager.getClusterEntityManagerFactory(),
                oldVirtualNodeEntityRepository,
                publisher,
                migrationUtility,
                membershipGroupReaderProvider,
                memberResourceClient,
                membershipQueryClient,
                externalMessagingRouteConfigGenerator,
                cordaAvroSerializationFactory,
                recordFactory,
                groupPolicyParser,
            ),

            VirtualNodeCreateRequest::class.java to CreateVirtualNodeOperationHandler(
                createVirtualNodeService,
                virtualNodeDbFactory,
                recordFactory,
                groupPolicyParser,
                externalMessagingRouteConfigGenerator,
                LoggerFactory.getLogger(CreateVirtualNodeOperationHandler::class.java),
                dbConnectionManager.getClusterEntityManagerFactory(),
            ),

            VirtualNodeDbConnectionUpdateRequest::class.java to UpdateVirtualNodeDbOperationHandler(
                dbConnectionManager.getClusterEntityManagerFactory(),
                updateVirtualNodeService,
                virtualNodeDbFactory,
                recordFactory,
                LoggerFactory.getLogger(UpdateVirtualNodeDbOperationHandler::class.java)
            )
        )

        val asyncOperationProcessor = VirtualNodeAsyncOperationProcessor(
            handlerMap,
            LoggerFactory.getLogger(VirtualNodeAsyncOperationProcessor::class.java)
        )

        return subscriptionFactory.createDurableSubscription(
            subscriptionConfig,
            asyncOperationProcessor,
            messagingConfig,
            null
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
     * [messagingConfig]. The subscription is to the [VIRTUAL_NODE_CREATION_REQUEST_TOPIC] topic, and handles requests
     * using a [VirtualNodeWriterProcessor].
     */
    private fun createRPCSubscription(
        messagingConfig: SmartConfig,
        bootConfig: SmartConfig,
        vNodePublisher: Publisher,
    ): RPCSubscription<VirtualNodeManagementRequest, VirtualNodeManagementResponse> {
        val rpcConfig = RPCConfig(
            GROUP_NAME,
            CLIENT_NAME_RPC,
            VIRTUAL_NODE_CREATION_REQUEST_TOPIC,
            VirtualNodeManagementRequest::class.java,
            VirtualNodeManagementResponse::class.java,
        )
        val virtualNodeEntityRepository =
            VirtualNodeEntityRepository(
                dbConnectionManager.getClusterEntityManagerFactory(),
                cpiCpkRepositoryFactory.createCpiMetadataRepository()
            )

        val virtualNodeRepository: VirtualNodeRepository = VirtualNodeRepositoryImpl()
        val virtualNodeOperationStatusHandler =
            VirtualNodeOperationStatusHandler(dbConnectionManager, virtualNodeRepository)

        val offlineDbDir = tempPathProvider.getOrCreate(bootConfig, OFFLINE_DB_DIR)
        val virtualNodeSchemaHandler = VirtualNodeSchemaHandler(
            offlineDbDir,
            dbConnectionManager,
            schemaMigrator,
            virtualNodeRepository
        )

        val processor = VirtualNodeWriterProcessor(
            vNodePublisher,
            dbConnectionManager,
            virtualNodeEntityRepository,
            virtualNodeOperationStatusHandler,
            virtualNodeSchemaHandler,
            cpkDbChangeLogRepository,
            virtualNodeRepository = virtualNodeRepository,
            migrationUtility = MigrationUtilityImpl(dbConnectionManager, schemaMigrator),
            jpaEntitiesRegistry
        )

        return subscriptionFactory.createRPCSubscription(rpcConfig, messagingConfig, processor)
    }
}
