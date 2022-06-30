package net.corda.membership.verification.service.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.membership.p2p.VerificationRequest
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
import net.corda.membership.verification.service.MembershipVerificationService
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Membership.Companion.MEMBERSHIP_VERIFICATION_TOPIC
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * Handles verification requests coming from MGM side.
 */
@Component(service = [MembershipVerificationService::class])
class MembershipVerificationServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
) : MembershipVerificationService {

    private companion object {
        val logger = contextLogger()

        @Volatile
        var dependencyHandle: RegistrationHandle? = null

        @Volatile
        var subHandle: RegistrationHandle? = null

        @Volatile
        var configHandle: AutoCloseable? = null

        @Volatile
        var subscription: Subscription<String, VerificationRequest>? = null

        const val GROUP_NAME = "membership.verification.service"
    }

    override val lifecycleCoordinatorName =
        LifecycleCoordinatorName.forComponent<MembershipVerificationService>()

    private val coordinator = coordinatorFactory.createCoordinator(
        lifecycleCoordinatorName,
        ::handleEvent
    )

    override val isRunning: Boolean
        get() = coordinator.status == LifecycleStatus.UP

    override fun start() {
        logger.info("Starting...")
        coordinator.start()
    }

    override fun stop() {
        logger.info("Stopping...")
        coordinator.stop()
    }

    private fun handleEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("Received event $event.")
        when(event) {
            is StartEvent -> handleStartEvent(coordinator)
            is StopEvent -> handleStopEvent(coordinator)
            is RegistrationStatusChangeEvent -> handleRegistrationStatusChangeEvent(event, coordinator)
            is ConfigChangedEvent -> handleConfigChangedEvent(event, coordinator)
        }
    }

    private fun handleStartEvent(coordinator: LifecycleCoordinator) {
        logger.info("Handling start event.")
        dependencyHandle?.close()
        dependencyHandle = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
            )
        )
    }

    private fun handleStopEvent(coordinator: LifecycleCoordinator) {
        logger.info("Handling stop event.")
        coordinator.updateStatus(
            LifecycleStatus.DOWN,
            "Component received stop event."
        )
        dependencyHandle?.close()
        dependencyHandle = null
        configHandle?.close()
        configHandle = null
        subHandle?.close()
        subHandle = null
        subscription?.close()
        subscription = null
    }

    private fun handleRegistrationStatusChangeEvent(event: RegistrationStatusChangeEvent, coordinator: LifecycleCoordinator) {
        logger.info("Handling registration changed event.")
        when(event.status) {
            LifecycleStatus.UP -> {
                if(event.registration == dependencyHandle) {
                    configHandle?.close()
                    configHandle = configurationReadService.registerComponentForUpdates(
                        coordinator,
                        setOf(BOOT_CONFIG, MESSAGING_CONFIG)
                    )
                } else if(event.registration == subHandle) {
                    coordinator.updateStatus(
                        LifecycleStatus.UP,
                        "Received config and started topic subscription."
                    )
                }
            }
            else -> {
                coordinator.updateStatus(
                    LifecycleStatus.DOWN,
                    "Dependency component is down."
                )
            }
        }
    }

    private fun handleConfigChangedEvent(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        logger.info("Handling config changed event.")
        subscription?.close()
        val messagingConfig = event.config.getConfig(MESSAGING_CONFIG)
        subscription = subscriptionFactory.createDurableSubscription(
            subscriptionConfig = SubscriptionConfig(
                GROUP_NAME,
                MEMBERSHIP_VERIFICATION_TOPIC
            ),
            processor = MembershipVerificationProcessor(cordaAvroSerializationFactory),
            messagingConfig = messagingConfig,
            null
        ).also {
            it.start()
            subHandle?.close()
            subHandle = coordinator.followStatusChangesByName(setOf(it.subscriptionName))
        }
    }
}