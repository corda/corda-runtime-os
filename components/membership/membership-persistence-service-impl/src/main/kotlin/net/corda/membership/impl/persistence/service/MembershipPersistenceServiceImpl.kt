package net.corda.membership.impl.persistence.service

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.async.MembershipPersistenceAsyncRequest
import net.corda.data.membership.db.request.async.MembershipPersistenceAsyncRequestState
import net.corda.data.membership.db.response.MembershipPersistenceResponse
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.membership.groupparams.writer.service.GroupParametersWriterService
import net.corda.membership.impl.persistence.service.handler.HandlerFactories
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.mtls.allowed.list.service.AllowedCertificatesReaderWriterService
import net.corda.membership.persistence.service.MembershipPersistenceService
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.schema.Schemas
import net.corda.schema.Schemas.Membership.MEMBERSHIP_DB_ASYNC_TOPIC
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.utilities.debug
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
@Component(service = [MembershipPersistenceService::class])
class MembershipPersistenceServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = DbConnectionManager::class)
    dbConnectionManager: DbConnectionManager,
    @Reference(service = JpaEntitiesRegistry::class)
    jpaEntitiesRegistry: JpaEntitiesRegistry,
    @Reference(service = MemberInfoFactory::class)
    memberInfoFactory: MemberInfoFactory,
    @Reference(service = CordaAvroSerializationFactory::class)
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    @Reference(service = VirtualNodeInfoReadService::class)
    virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = KeyEncodingService::class)
    keyEncodingService: KeyEncodingService,
    @Reference(service = PlatformInfoProvider::class)
    platformInfoProvider: PlatformInfoProvider,
    @Reference(service = AllowedCertificatesReaderWriterService::class)
    allowedCertificatesReaderWriterService: AllowedCertificatesReaderWriterService,
    @Reference(service = GroupParametersWriterService::class)
    groupParametersWriterService: GroupParametersWriterService,
    @Reference(service = GroupParametersFactory::class)
    groupParametersFactory: GroupParametersFactory,
) : MembershipPersistenceService {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        const val GROUP_NAME = "membership.db.persistence"
        const val CLIENT_NAME = "membership.db.persistence"
        const val ASYNC_GROUP_NAME = "membership.db.persistence.async"

        private val clock: Clock = UTCClock()
    }

    private val coordinator = coordinatorFactory.createCoordinator<MembershipPersistenceService>(::handleEvent)
    private var rpcSubscription: RPCSubscription<MembershipPersistenceRequest, MembershipPersistenceResponse>? = null
    private var asyncSubscription:
        StateAndEventSubscription<String, MembershipPersistenceAsyncRequestState, MembershipPersistenceAsyncRequest>? = null
    private val retryManager = MembershipPersistenceAsyncRetryManager(
        coordinatorFactory,
        publisherFactory,
        clock,
    )

    private var dependencyServiceHandle: RegistrationHandle? = null
    private var configHandle: AutoCloseable? = null

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        logger.info("Starting component.")
        coordinator.start()
    }

    override fun stop() {
        logger.info("Stopping component.")
        coordinator.stop()
    }

    private fun handleEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> handleStartEvent(coordinator)
            is StopEvent -> handleStopEvent(coordinator)
            is RegistrationStatusChangeEvent -> handleRegistrationStatusChangedEvent(event, coordinator)
            is ConfigChangedEvent -> handleConfigChangedEvent(event, coordinator)
        }
    }

    private fun handleStartEvent(coordinator: LifecycleCoordinator) {
        logger.debug { "Handling start event." }
        dependencyServiceHandle?.close()
        dependencyServiceHandle = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                LifecycleCoordinatorName.forComponent<DbConnectionManager>(),
                LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
                LifecycleCoordinatorName.forComponent<AllowedCertificatesReaderWriterService>(),
            )
        )
    }

    private fun handleStopEvent(coordinator: LifecycleCoordinator) {
        logger.debug { "Handling stop event." }
        coordinator.updateStatus(
            LifecycleStatus.DOWN,
            "Component received stop event."
        )
        dependencyServiceHandle?.close()
        dependencyServiceHandle = null
        configHandle?.close()
        configHandle = null
        rpcSubscription?.close()
        rpcSubscription = null
        asyncSubscription?.close()
        asyncSubscription = null
        retryManager.stop()
    }

    private fun handleRegistrationStatusChangedEvent(event: RegistrationStatusChangeEvent, coordinator: LifecycleCoordinator) {
        logger.debug { "Handling registration changed event. Registration event status=${event.status}" }
        when (event.status) {
            LifecycleStatus.UP -> {
                    configHandle?.close()
                    configHandle = configurationReadService.registerComponentForUpdates(
                        coordinator,
                        setOf(BOOT_CONFIG, MESSAGING_CONFIG)
                    )
            }
            else -> {
                coordinator.updateStatus(LifecycleStatus.DOWN, "Dependencies are down.")
            }
        }
    }

    private val handlers by lazy {
        HandlerFactories(
            clock,
            dbConnectionManager,
            jpaEntitiesRegistry,
            memberInfoFactory,
            cordaAvroSerializationFactory,
            virtualNodeInfoReadService,
            keyEncodingService,
            platformInfoProvider,
            groupParametersWriterService,
            groupParametersFactory,
            allowedCertificatesReaderWriterService,
        )
    }

    private fun handleConfigChangedEvent(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        logger.info("Handling config changed event.")
        rpcSubscription?.close()
        val messagingConfig = event.config.getConfig(MESSAGING_CONFIG)
        subscriptionFactory.createRPCSubscription(
            rpcConfig = RPCConfig(
                groupName = GROUP_NAME,
                clientName = CLIENT_NAME,
                requestTopic = Schemas.Membership.MEMBERSHIP_DB_RPC_TOPIC,
                requestType = MembershipPersistenceRequest::class.java,
                responseType = MembershipPersistenceResponse::class.java
            ),
            responderProcessor = MembershipPersistenceRPCProcessor(
                handlers
            ),
            messagingConfig = messagingConfig
        ).also {
            rpcSubscription = it
            it.start()
        }
        asyncSubscription?.close()
        retryManager.start(messagingConfig)
        subscriptionFactory.createStateAndEventSubscription(
            subscriptionConfig = SubscriptionConfig(
                groupName = ASYNC_GROUP_NAME,
                eventTopic = MEMBERSHIP_DB_ASYNC_TOPIC,
            ),
            processor = MembershipPersistenceAsyncProcessor(
                handlers
            ),
            messagingConfig = messagingConfig,
            stateAndEventListener = retryManager
        ).also {
            asyncSubscription = it
            it.start()
        }

        coordinator.updateStatus(
            LifecycleStatus.UP,
            "Received config and started Membership persistence topic subscriptions."
        )
    }
}
