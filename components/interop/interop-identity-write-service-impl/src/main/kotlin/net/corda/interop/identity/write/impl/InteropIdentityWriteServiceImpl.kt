package net.corda.interop.identity.write.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.interop.InteropAliasIdentity
import net.corda.interop.identity.write.InteropIdentityWriteService
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
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.schema.configuration.ConfigKeys
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference


@Suppress("ForbiddenComment")
@Component(service = [InteropIdentityWriteService::class])
class InteropIdentityWriteServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : InteropIdentityWriteService, LifecycleEventHandler {
    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val CLIENT_ID = "INTEROP_IDENTITY_WRITER"
    }

    private val coordinatorName = LifecycleCoordinatorName.forComponent<InteropIdentityWriteService>()
    private val coordinator = coordinatorFactory.createCoordinator(coordinatorName, this)

    private var registration: RegistrationHandle? = null
    private var configSubscription: AutoCloseable? = null

    private val publisher: AtomicReference<Publisher?> = AtomicReference()
    private val producer = InteropIdentityProducer(publisher)

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun addInteropIdentity(
        holdingIdentityShortHash: String,
        interopGroupId: String,
        newIdentityName: String
    ) {
        val interopAliasIdentity = InteropAliasIdentity().apply {
            aliasX500Name = newIdentityName
            groupId = interopGroupId
            // TODO: Figure out what value goes here!
            hostingVnode = "?"
        }

        producer.publishInteropIdentity(holdingIdentityShortHash, interopAliasIdentity)
    }

    override fun start() {
        // Use debug rather than info
        log.info("Component starting")
        coordinator.start()
    }

    override fun stop() {
        // Use debug rather than info
        log.info("Component stopping")
        coordinator.stop()
    }

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is StopEvent -> onStopEvent()
            is ConfigChangedEvent -> onConfigChangeEvent(event, coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event, coordinator)
            else -> {
                log.error("Unexpected event: '$event'")
            }
        }
    }

    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        configurationReadService.start()
        registration?.close()
        registration = coordinator.followStatusChangesByName(setOf(
            LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
        ))
    }

    private fun onStopEvent() {
        configSubscription?.close()
        configSubscription = null
        registration?.close()
        registration = null
        publisher.get()?.close()
        publisher.set(null)
    }

    private fun onConfigChangeEvent(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        val config = event.config[ConfigKeys.MESSAGING_CONFIG] ?: return

        // TODO: Use debug rather than info
        log.info("Processing config update")

        coordinator.updateStatus(LifecycleStatus.DOWN)

        publisher.get()?.close()
        publisher.set(publisherFactory.createPublisher(PublisherConfig(CLIENT_ID), config))

        coordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun onRegistrationStatusChangeEvent(event: RegistrationStatusChangeEvent, coordinator: LifecycleCoordinator) {
        if (event.status == LifecycleStatus.UP) {
            configSubscription = configurationReadService.registerComponentForUpdates(coordinator, setOf(
                ConfigKeys.MESSAGING_CONFIG
            ))
        } else {
            configSubscription?.close()
            configSubscription = null
            publisher.get()?.close()
            publisher.set(null)
        }
    }
}
