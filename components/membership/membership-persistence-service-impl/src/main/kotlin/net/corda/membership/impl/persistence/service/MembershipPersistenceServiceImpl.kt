package net.corda.membership.impl.persistence.service

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.membership.db.request.MembershipPersistenceRequest
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
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.mtls.allowed.list.service.AllowedCertificatesReaderWriterService
import net.corda.membership.persistence.service.MembershipPersistenceService
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("LongParameterList")
@Component(service = [MembershipPersistenceService::class])
class MembershipPersistenceServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = JpaEntitiesRegistry::class)
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    @Reference(service = MemberInfoFactory::class)
    private val memberInfoFactory: MemberInfoFactory,
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService,
    @Reference(service = PlatformInfoProvider::class)
    private val platformInfoProvider: PlatformInfoProvider,
    @Reference(service = AllowedCertificatesReaderWriterService::class)
    private val allowedCertificatesReaderWriterService: AllowedCertificatesReaderWriterService,
) : MembershipPersistenceService {

    private companion object {
        val logger = contextLogger()

        const val GROUP_NAME = "membership.db.persistence"
        const val CLIENT_NAME = "membership.db.persistence"

        private val clock: Clock = UTCClock()
    }

    private val coordinator = coordinatorFactory.createCoordinator<MembershipPersistenceService>(::handleEvent)
    private var rpcSubscription: RPCSubscription<MembershipPersistenceRequest, MembershipPersistenceResponse>? = null

    private var dependencyServiceHandle: RegistrationHandle? = null
    private var subHandle: RegistrationHandle? = null
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
        logger.info("Received event $event.")
        when (event) {
            is StartEvent -> handleStartEvent(coordinator)
            is StopEvent -> handleStopEvent(coordinator)
            is RegistrationStatusChangeEvent -> handleRegistrationStatusChangedEvent(event, coordinator)
            is ConfigChangedEvent -> handleConfigChangedEvent(event, coordinator)
        }
    }

    private fun handleStartEvent(coordinator: LifecycleCoordinator) {
        logger.info("Handling start event.")
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
        logger.info("Handling stop event.")
        coordinator.updateStatus(
            LifecycleStatus.DOWN,
            "Component received stop event."
        )
        dependencyServiceHandle?.close()
        dependencyServiceHandle = null
        subHandle?.close()
        subHandle = null
        configHandle?.close()
        configHandle = null
        rpcSubscription?.close()
        rpcSubscription = null
    }

    private fun handleRegistrationStatusChangedEvent(event: RegistrationStatusChangeEvent, coordinator: LifecycleCoordinator) {
        logger.info("Handling registration changed event.")
        when (event.status) {
            LifecycleStatus.UP -> {
                if (event.registration == dependencyServiceHandle) {
                    configHandle?.close()
                    configHandle = configurationReadService.registerComponentForUpdates(
                        coordinator,
                        setOf(BOOT_CONFIG, MESSAGING_CONFIG)
                    )
                } else if (event.registration == subHandle) {
                    coordinator.updateStatus(
                        LifecycleStatus.UP,
                        "Received config and started RPC topic subscription."
                    )
                }
            }
            else -> {
                coordinator.updateStatus(LifecycleStatus.DOWN, "Dependencies are down.")
            }
        }
    }

    private fun handleConfigChangedEvent(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        logger.info("Handling config changed event.")
        subHandle?.close()
        subHandle = null
        rpcSubscription?.close()
        val messagingConfig = event.config.getConfig(MESSAGING_CONFIG)
        rpcSubscription = subscriptionFactory.createRPCSubscription(
            rpcConfig = RPCConfig(
                groupName = GROUP_NAME,
                clientName = CLIENT_NAME,
                requestTopic = Schemas.Membership.MEMBERSHIP_DB_RPC_TOPIC,
                requestType = MembershipPersistenceRequest::class.java,
                responseType = MembershipPersistenceResponse::class.java
            ),
            responderProcessor = MembershipPersistenceRPCProcessor(
                clock,
                dbConnectionManager,
                jpaEntitiesRegistry,
                memberInfoFactory,
                cordaAvroSerializationFactory,
                virtualNodeInfoReadService,
                keyEncodingService,
                platformInfoProvider,
                allowedCertificatesReaderWriterService,
            ),
            messagingConfig = messagingConfig
        ).also {
            it.start()
            subHandle = coordinator.followStatusChangesByName(setOf(it.subscriptionName))
        }
    }
}
