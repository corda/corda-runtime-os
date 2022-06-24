package net.corda.membership.verification.service.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.membership.verification.request.VerificationRequest
import net.corda.data.membership.verification.response.VerificationResponse
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.verification.service.VerificationService
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Membership.Companion.MEMBERSHIP_VERIFICATION_TOPIC
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * Handles verification requests coming from MGM side.
 */
@Component(service = [VerificationService::class])
class VerificationServiceImpl(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory
) : VerificationService, Lifecycle {

    private companion object {
        val logger = contextLogger()

        @Volatile
        var componentHandle: AutoCloseable? = null

        @Volatile
        var configHandle: AutoCloseable? = null

        @Volatile
        var subscription: RPCSubscription<VerificationRequest, VerificationResponse>? = null

        const val GROUP_NAME = "membership.verification.service"
        const val CLIENT_NAME = "membership.verification.service"
    }

    override val lifecycleCoordinatorName =
        LifecycleCoordinatorName.forComponent<VerificationServiceImpl>()

    private val coordinator = coordinatorFactory.createCoordinator(
        lifecycleCoordinatorName,
        ::handleEvent
    )

    private val processor = VerificationServiceProcessor()

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
            is StartEvent -> {
                logger.info("Handling start event.")
                componentHandle?.close()
                componentHandle = coordinator.followStatusChangesByName(
                    setOf(

                    )
                )
            }
            is StopEvent -> {
                logger.info("Handling stop event.")
                coordinator.updateStatus(
                    LifecycleStatus.DOWN,
                    "Component received stop event."
                )
                componentHandle?.close()
                subscription?.close()
                subscription = null
            }
            is RegistrationStatusChangeEvent -> {
                logger.info("Handling registration changed event.")
                when(event.status) {
                    LifecycleStatus.UP -> {
                        configHandle?.close()
                        configHandle = configurationReadService.registerComponentForUpdates(
                            coordinator,
                            setOf(BOOT_CONFIG, MESSAGING_CONFIG)
                        )
                    }
                    else -> {
                        coordinator.updateStatus(
                            LifecycleStatus.DOWN,
                            "Dependency component is down."
                        )
                    }
                }
            }
            is ConfigChangedEvent -> {
                logger.info("Handling config changed event.")
                subscription?.close()
                val messagingConfig = event.config.getConfig(MESSAGING_CONFIG)
                subscription = subscriptionFactory.createRPCSubscription(
                    rpcConfig = RPCConfig(
                        groupName = GROUP_NAME,
                        clientName = CLIENT_NAME,
                        requestTopic = MEMBERSHIP_VERIFICATION_TOPIC,
                        requestType = VerificationRequest::class.java,
                        responseType = VerificationResponse::class.java
                    ),
                    responderProcessor = processor,
                    messagingConfig = messagingConfig
                ).also { it.start() }
            }
        }
    }
}