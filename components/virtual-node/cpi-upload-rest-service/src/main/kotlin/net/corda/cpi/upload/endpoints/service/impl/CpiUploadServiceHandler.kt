package net.corda.cpi.upload.endpoints.service.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.cpiupload.CpiUploadManager
import net.corda.libs.cpiupload.CpiUploadManagerFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.VisibleForTesting
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Registers to [ConfigurationReadService] for config updates, and on new config updates creates a new [CpiUploadManager]
 * which also exposes.
 */
class CpiUploadServiceHandler(
    private val cpiUploadManagerFactory: CpiUploadManagerFactory,
    private val configReadService: ConfigurationReadService,
    private val publisherFactory: PublisherFactory,
    private val subscriptionFactory: SubscriptionFactory,
) : LifecycleEventHandler {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @VisibleForTesting
    internal var configReadServiceRegistrationHandle: RegistrationHandle? = null

    internal var cpiUploadManager: CpiUploadManager? = null

    private var configSubscription: AutoCloseable? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event, coordinator)
            is ConfigChangedEvent -> onConfigChangedEvent(event, coordinator)
            is StopEvent -> onStopEvent(coordinator)
        }
    }

    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        configReadServiceRegistrationHandle?.close()
        configReadServiceRegistrationHandle = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
            )
        )
    }

    private fun onStopEvent(coordinator: LifecycleCoordinator) {
        closeResources()
        coordinator.updateStatus(LifecycleStatus.DOWN)
    }

    private fun onRegistrationStatusChangeEvent(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator
    ) {
        if (event.status == LifecycleStatus.UP) {
            log.info("Registering to ConfigurationReadService")
            configSubscription = configReadService.registerComponentForUpdates(
                coordinator,
                setOf(
                    ConfigKeys.MESSAGING_CONFIG,
                    ConfigKeys.BOOT_CONFIG,
                    ConfigKeys.REST_CONFIG
                )
            )
        } else {
            closeResources()
            coordinator.updateStatus(event.status)
        }
    }

    // We only receive this event when we all keys are available as per [registerComponentForUpdates]
    private fun onConfigChangedEvent(
        event: ConfigChangedEvent,
        coordinator: LifecycleCoordinator
    ) {
        log.info("CPI Upload Service Handler event - config changed")
        val messagingConfig = event.config.getConfig(ConfigKeys.MESSAGING_CONFIG)
        cpiUploadManager?.close()
        cpiUploadManager = cpiUploadManagerFactory.create(
            messagingConfig,
            publisherFactory,
            subscriptionFactory
        )

        coordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun closeResources() {
        configReadServiceRegistrationHandle?.close()
        configReadServiceRegistrationHandle = null
        configSubscription?.close()
        configSubscription = null
        cpiUploadManager?.close()
        cpiUploadManager = null
    }
}