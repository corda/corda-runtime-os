package net.corda.membership.staticnetwork

import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.schema.configuration.ConfigKeys.Companion.MESSAGING_CONFIG

interface RegistrationServiceLifecycleHandler : LifecycleEventHandler {
    /**
     * Publisher for Kafka messaging. Recreated after every [MESSAGING_CONFIG] change.
     */
    val publisher: Publisher
}

open class RegistrationServiceLifecycleHandlerImpl(
    staticMemberRegistrationService: StaticMemberRegistrationService
) : RegistrationServiceLifecycleHandler {
    // for watching the config changes
    private var configHandle: AutoCloseable? = null
    // for checking the components' health
    private var componentHandle: AutoCloseable? = null

    private val publisherFactory = staticMemberRegistrationService.publisherFactory

    private val configurationReadService = staticMemberRegistrationService.configurationReadService

    private var _publisher: Publisher? = null

    override val publisher: Publisher
        get() = _publisher ?: throw IllegalArgumentException("Publisher is not initialized.")

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when(event) {
            is StartEvent -> handleStartEvent(coordinator)
            is StopEvent -> handleStopEvent()
            is RegistrationStatusChangeEvent -> handleRegistrationChangeEvent(event, coordinator)
            is MessagingConfigurationReceived -> handleConfigChange(event)
        }
    }

    private fun handleStartEvent(coordinator: LifecycleCoordinator) {
        componentHandle = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
                LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
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
                configHandle = configurationReadService.registerForUpdates(
                    MessagingConfigurationHandler(coordinator)
                )
                coordinator.updateStatus(LifecycleStatus.UP)
            }
            else -> {
                coordinator.updateStatus(LifecycleStatus.DOWN)
                configHandle?.close()
            }
        }
    }

    // re-creates the publisher with the new config
    private fun handleConfigChange(event: MessagingConfigurationReceived) {
        _publisher?.close()
        _publisher = publisherFactory.createPublisher(
            PublisherConfig("static-member-registration-service"),
            event.config
        )
        _publisher?.start()
    }
}

class MessagingConfigurationHandler(val coordinator: LifecycleCoordinator) : ConfigurationHandler {
    override fun onNewConfiguration(changedKeys: Set<String>, config: Map<String, SmartConfig>) {
        if(MESSAGING_CONFIG in changedKeys) {
            coordinator.postEvent(MessagingConfigurationReceived(config[MESSAGING_CONFIG]!!))
        }
    }
}

data class MessagingConfigurationReceived(val config: SmartConfig) : LifecycleEvent