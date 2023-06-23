package net.corda.interop.write.service.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.interop.InteropAliasIdentity
import net.corda.interop.write.service.InteropWriteService
import net.corda.interop.core.AliasIdentity
import net.corda.interop.write.service.producer.AliasIdentityProducer
import net.corda.interop.write.service.producer.HostedIdentityProducer
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.ConfigKeys.RECONCILIATION_CONFIG
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

@Suppress("TooManyFunctions")
@Component(service = [InteropWriteService::class])
class InteropWriteServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : InteropWriteService, LifecycleEventHandler {
    companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val CONFIG_HANDLE = "CONFIG_HANDLE"
        const val REGISTRATION = "REGISTRATION"
    }

    private val coordinator = coordinatorFactory.createCoordinator<InteropWriteService>(this)
    private val publisher: AtomicReference<Publisher?> = AtomicReference()
    private var registration: RegistrationHandle? = null
    private var configSubscription: AutoCloseable? = null
    private val aliasProducer = AliasIdentityProducer(publisher)
    private val hostedIdentityProducer = HostedIdentityProducer(publisher)

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event, coordinator)
            is ConfigChangedEvent -> onConfigChangedEvent(event, coordinator)
            is StopEvent -> onStopEvent()
        }
    }

    /**
     * We depend on the [ConfigurationReadService] so we 'listen' to [RegistrationStatusChangeEvent]
     * to tell us when it is ready so we can register ourselves to handle config updates.
     */
    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        coordinator.createManagedResource(REGISTRATION) {
            coordinator.followStatusChangesByName(
                setOf(
                    LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                )
            )
        }
    }

    /**
     * If the thing(s) we depend on are up (only the [ConfigurationReadService]),
     * then register `this` for config updates
     */
    private fun onRegistrationStatusChangeEvent(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator
    ) {
        if (event.status == LifecycleStatus.UP) {
            coordinator.createManagedResource(CONFIG_HANDLE) {
                configReadService.registerComponentForUpdates(
                    coordinator,
                    setOf(
                        BOOT_CONFIG,
                        MESSAGING_CONFIG,
                        RECONCILIATION_CONFIG,
                    )
                )
            }
            coordinator.updateStatus(LifecycleStatus.UP)
        } else {
            coordinator.updateStatus(LifecycleStatus.DOWN)
            closeResources()
        }
    }

    private fun onConfigChangedEvent(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        val messagingConfig = event.config.getConfig(MESSAGING_CONFIG)
        val reconciliationConfig = event.config.getConfig(RECONCILIATION_CONFIG)
        logger.info("messagingConfig: $messagingConfig and reconciliationConfig : $reconciliationConfig")

        coordinator.updateStatus(LifecycleStatus.UP)
    }

    /**
     * Close the registration.
     */
    private fun onStopEvent() {
        logger.info("Component stopping")
        coordinator.stop()
    }

    override fun publishAliasIdentity(aliasIdentity: AliasIdentity) {
        aliasProducer.publishAliasIdentity(
            aliasIdentity.realHoldingIdentityShortHash.value + ":" + aliasIdentity.aliasShortHash.value,
            InteropAliasIdentity(
                aliasIdentity.groupId.toString(),
                aliasIdentity.x500Name.toString(),
                aliasIdentity.hostingVnode
            )
        )
    }

    override fun publishHostedAliasIdentity(aliasIdentity: AliasIdentity) {
        hostedIdentityProducer.publishHostedAliasIdentity(aliasIdentity)
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        logger.debug("Interop Write Service starting")
        coordinator.start()
    }

    override fun stop() {
        logger.debug("Interop Write Service stopping")
        coordinator.stop()
        closeResources()
    }

    private fun closeResources() {
        configSubscription?.close()
        configSubscription = null
        registration?.close()
        registration = null
        publisher.get()?.close()
        publisher.set(null)
    }
}