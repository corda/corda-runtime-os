package net.corda.membership.impl.p2p

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.hes.StableKeyPairDecryptor
import net.corda.libs.configuration.SmartConfig
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
import net.corda.schema.Schemas.P2P.Companion.P2P_IN_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_MARKERS
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.KeyEncodingService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("LongParameterList")
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
    @Reference(service = MembershipGroupReaderProvider::class)
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
) : MembershipP2PReadService {

    companion object {
        private val logger = contextLogger()

        const val CONSUMER_GROUP = "membership_p2p_read"
        const val MARKER_CONSUMER_GROUP = "membership_p2p_read_markers"
    }

    private val coordinator = lifecycleCoordinatorFactory
        .createCoordinator<MembershipP2PReadService>(::handleEvent)

    private var registrationHandle: RegistrationHandle? = null
    private var subRegistrationHandle: RegistrationHandle? = null
    private var configHandle: AutoCloseable? = null

    private var subscriptions: Collection<Subscription<String, *>>? = null

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
                configHandle?.close()
                configHandle = null
                stopSubscriptions()
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
                    stopSubscriptions()
                }
            }
            is ConfigChangedEvent -> {
                val messagingConfig = event.config.getConfig(MESSAGING_CONFIG)
                createSubscriptions(messagingConfig)
            }
        }
    }
    private fun stopSubscriptions() {
        subRegistrationHandle?.close()
        subRegistrationHandle = null
        subscriptions?.forEach {
            it.close()
        }
        subscriptions = null
    }

    private fun createSubscriptions(messagingConfig: SmartConfig) {
        stopSubscriptions()
        subscriptions = listOf(
            subscriptionFactory.createDurableSubscription(
                SubscriptionConfig(
                    CONSUMER_GROUP,
                    P2P_IN_TOPIC
                ),
                MembershipP2PProcessor(
                    avroSchemaRegistry,
                    stableKeyPairDecryptor,
                    keyEncodingService,
                    membershipGroupReaderProvider
                ),
                messagingConfig,
                null
            ),
            subscriptionFactory.createDurableSubscription(
                SubscriptionConfig(
                    MARKER_CONSUMER_GROUP,
                    P2P_OUT_MARKERS,
                ),
                MembershipP2PMarkersProcessor(),
                messagingConfig,
                null,
            )
        ).also {
            val names = it.onEach { subscription ->
                subscription.start()
            }.map { subscription ->
                subscription.subscriptionName
            }.toSet()
            subRegistrationHandle = coordinator.followStatusChangesByName(names)
        }
    }
}