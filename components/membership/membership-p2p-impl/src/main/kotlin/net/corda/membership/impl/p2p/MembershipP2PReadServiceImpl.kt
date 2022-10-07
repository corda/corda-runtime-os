package net.corda.membership.impl.p2p

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.ecies.StableKeyPairDecryptor
import net.corda.data.CordaAvroSerializationFactory
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
import net.corda.membership.p2p.MembershipP2PReadService
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.app.AppMessage
import net.corda.schema.Schemas.P2P.Companion.P2P_IN_TOPIC
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.KeyEncodingService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [MembershipP2PReadService::class])
class MembershipP2PReadServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = AvroSchemaRegistry::class)
    private val avroSchemaRegistry: AvroSchemaRegistry,
    @Reference(service = StableKeyPairDecryptor::class)
    private val stableKeyPairDecryptor: StableKeyPairDecryptor,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService,
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    @Reference(service = MembershipGroupReaderProvider::class)
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
) : MembershipP2PReadService {

    companion object {
        private val logger = contextLogger()

        const val CONSUMER_GROUP = "membership_p2p_read"
    }

    private val coordinator = lifecycleCoordinatorFactory
        .createCoordinator<MembershipP2PReadService>(::handleEvent)

    private var registrationHandle: RegistrationHandle? = null
    private var subRegistrationHandle: RegistrationHandle? = null
    private var configHandle: AutoCloseable? = null

    private var subscription: Subscription<String, AppMessage>? = null

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
                registrationHandle?.close()
                registrationHandle = coordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                        LifecycleCoordinatorName.forComponent<StableKeyPairDecryptor>(),
                        LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
                    )
                )
            }
            is StopEvent -> {
                coordinator.updateStatus(LifecycleStatus.DOWN, "Received stop event.")
                registrationHandle?.close()
                registrationHandle = null
                subRegistrationHandle?.close()
                subRegistrationHandle = null
                configHandle?.close()
                configHandle = null
                subscription?.close()
                subscription = null
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    if (event.registration == registrationHandle) {
                        logger.info("Dependency services are UP. Registering to receive configuration.")
                        configHandle?.close()
                        configHandle = configurationReadService.registerComponentForUpdates(
                            coordinator,
                            setOf(MESSAGING_CONFIG, BOOT_CONFIG)
                        )
                    } else if (event.registration == subRegistrationHandle) {
                        logger.info("Subscription is UP. Component started.")
                        coordinator.updateStatus(LifecycleStatus.UP, "Started subscription for incoming P2P messages.")
                    }
                } else {
                    logger.info("Setting deactive state due to receiving registration status ${event.status}")
                    coordinator.updateStatus(LifecycleStatus.DOWN)
                    subRegistrationHandle?.close()
                    subRegistrationHandle = null
                    subscription?.close()
                    subscription = null
                }
            }
            is ConfigChangedEvent -> {
                val messagingConfig = event.config.getConfig(MESSAGING_CONFIG)
                subRegistrationHandle?.close()
                subRegistrationHandle = null
                subscription?.close()
                subscription = subscriptionFactory.createDurableSubscription(
                    SubscriptionConfig(
                        CONSUMER_GROUP,
                        P2P_IN_TOPIC
                    ),
                    MembershipP2PProcessor(
                        avroSchemaRegistry,
                        stableKeyPairDecryptor,
                        keyEncodingService,
                        cordaAvroSerializationFactory,
                        membershipGroupReaderProvider
                    ),
                    messagingConfig,
                    null
                ).also {
                    it.start()
                    subRegistrationHandle = coordinator.followStatusChangesByName(setOf(it.subscriptionName))
                }
            }
        }
    }
}