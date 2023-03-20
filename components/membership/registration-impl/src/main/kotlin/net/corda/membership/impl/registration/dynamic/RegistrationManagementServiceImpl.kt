package net.corda.membership.impl.registration.dynamic

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.state.RegistrationState
import net.corda.libs.configuration.helper.getConfig
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
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.registration.RegistrationManagementService
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Membership.REGISTRATION_COMMAND_TOPIC
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MEMBERSHIP_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
@Component(service = [RegistrationManagementService::class])
class RegistrationManagementServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = MemberInfoFactory::class)
    private val memberInfoFactory: MemberInfoFactory,
    @Reference(service = MembershipGroupReaderProvider::class)
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    @Reference(service = MembershipPersistenceClient::class)
    private val membershipPersistenceClient: MembershipPersistenceClient,
    @Reference(service = MembershipQueryClient::class)
    private val membershipQueryClient: MembershipQueryClient,
    @Reference(service = CryptoOpsClient::class)
    private val cryptoOpsClient: CryptoOpsClient,
    @Reference(service = CipherSchemeMetadata::class)
    private val cipherSchemeMetadata: CipherSchemeMetadata,
    @Reference(service = MerkleTreeProvider::class)
    private val merkleTreeProvider: MerkleTreeProvider,
    @Reference(service = GroupParametersWriterService::class)
    private val groupParametersWriterService: GroupParametersWriterService,
    @Reference(service = GroupParametersFactory::class)
    private val groupParametersFactory: GroupParametersFactory,
) : RegistrationManagementService {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private const val CONSUMER_GROUP = "membership.registration.processor.group"

        private val clock: Clock = UTCClock()
    }

    private val coordinator = lifecycleCoordinatorFactory
        .createCoordinator<RegistrationManagementService>(::handleEvent)

    private var dependencyServiceRegistration: RegistrationHandle? = null
    private var subRegistration: RegistrationHandle? = null
    private var configHandle: AutoCloseable? = null

    private var subscription: StateAndEventSubscription<String, RegistrationState, RegistrationCommand>? = null

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
        logger.info("Received event {}", event)
        when (event) {
            is StartEvent -> {
                logger.info("Processing start event.")
                dependencyServiceRegistration?.close()
                dependencyServiceRegistration = coordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                        LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
                        LifecycleCoordinatorName.forComponent<MembershipPersistenceClient>(),
                        LifecycleCoordinatorName.forComponent<MembershipQueryClient>(),
                    )
                )
            }
            is StopEvent -> {
                coordinator.updateStatus(LifecycleStatus.DOWN, "Received stop event.")
                dependencyServiceRegistration?.close()
                dependencyServiceRegistration = null
                subRegistration?.close()
                subRegistration = null
                configHandle?.close()
                configHandle = null
                subscription?.close()
                subscription = null
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    if (event.registration == dependencyServiceRegistration) {
                        logger.info("Dependency services are UP. Registering to receive configuration.")
                        configHandle?.close()
                        configHandle = configurationReadService.registerComponentForUpdates(
                            coordinator,
                            setOf(MESSAGING_CONFIG, BOOT_CONFIG, MEMBERSHIP_CONFIG)
                        )
                    } else if (event.registration == subRegistration) {
                        logger.info("Received config, started subscriptions and setting status to UP")
                        coordinator.updateStatus(LifecycleStatus.UP, "Received config, started subscriptions and setting status to UP")
                    }
                } else {
                    logger.info("Setting deactive state due to receiving registration status ${event.status}")
                    coordinator.updateStatus(LifecycleStatus.DOWN)
                    subRegistration?.close()
                    subRegistration = null
                    subscription?.close()
                    subscription = null
                }
            }
            is ConfigChangedEvent -> {
                val messagingConfig = event.config.getConfig(MESSAGING_CONFIG)
                val membershipConfig = event.config.getConfig(MEMBERSHIP_CONFIG)
                subRegistration?.close()
                subRegistration = null
                subscription?.close()
                subscription = subscriptionFactory.createStateAndEventSubscription(
                    SubscriptionConfig(
                        CONSUMER_GROUP,
                        REGISTRATION_COMMAND_TOPIC
                    ),
                    RegistrationProcessor(
                        clock,
                        memberInfoFactory,
                        membershipGroupReaderProvider,
                        cordaAvroSerializationFactory,
                        membershipPersistenceClient,
                        membershipQueryClient,
                        cryptoOpsClient,
                        cipherSchemeMetadata,
                        merkleTreeProvider,
                        membershipConfig,
                        groupParametersWriterService,
                        groupParametersFactory,
                    ),
                    messagingConfig
                ).also {
                    it.start()
                    subRegistration = coordinator.followStatusChangesByName(setOf(it.subscriptionName))
                }
            }
        }
    }
}
