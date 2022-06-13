package net.corda.membership.impl.registration.dynamic.mgm

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG

class MGMRegistrationServiceLifecycleHandler(
    mgmRegistrationService: MGMRegistrationService
) : LifecycleEventHandler {
    // for watching the config changes
    private var configHandle: AutoCloseable? = null
    // for checking the components' health
    private var componentHandle: AutoCloseable? = null

    private val publisherFactory = mgmRegistrationService.publisherFactory

    private val configurationReadService = mgmRegistrationService.configurationReadService

    private var _publisher: Publisher? = null

    /**
     * Publisher for Kafka messaging. Recreated after every [MESSAGING_CONFIG] change.
     */
    val publisher: Publisher
        get() = _publisher ?: throw IllegalArgumentException("Publisher is not initialized.")

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when(event) {
            is StartEvent -> handleStartEvent(coordinator)
            is StopEvent -> handleStopEvent()
            is RegistrationStatusChangeEvent -> handleRegistrationChangeEvent(event, coordinator)
            is ConfigChangedEvent -> handleConfigChange(event, coordinator)
        }
    }

    private fun handleStartEvent(coordinator: LifecycleCoordinator) {
        componentHandle?.close()
        componentHandle = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
            )
        )
    }

    private fun handleStopEvent() {
        componentHandle?.close()
        configHandle?.close()
        _publisher?.close()
        _publisher = null
    }

    private fun handleRegistrationChangeEvent(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator
    ) {
        when (event.status) {
            LifecycleStatus.UP -> {
                configHandle?.close()
                configHandle = configurationReadService.registerComponentForUpdates(
                    coordinator,
                    setOf(ConfigKeys.BOOT_CONFIG, MESSAGING_CONFIG)
                )
            }
            else -> {
                coordinator.updateStatus(LifecycleStatus.DOWN)
                configHandle?.close()
            }
        }
    }

    // re-creates the publisher with the new config, sets the lifecycle status to UP when the publisher is ready for the first time
    private fun handleConfigChange(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        _publisher?.close()
        _publisher = publisherFactory.createPublisher(
            PublisherConfig("mgm-registration-service"),
            event.config.getConfig(MESSAGING_CONFIG)
        )
        _publisher?.start()
        if(coordinator.status != LifecycleStatus.UP) {
            coordinator.updateStatus(LifecycleStatus.UP)
        }
    }
}
