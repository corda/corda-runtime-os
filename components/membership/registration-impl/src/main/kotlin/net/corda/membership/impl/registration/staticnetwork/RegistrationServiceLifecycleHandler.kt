package net.corda.membership.impl.registration.staticnetwork

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.hsm.HSMRegistrationClient
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG

class RegistrationServiceLifecycleHandler(
    staticMemberRegistrationService: StaticMemberRegistrationService
) : LifecycleEventHandler {
    companion object {

        // Keys for resources managed by this components lifecycle coordinator. Note that this class is reliant on a
        // coordinator created elsewhere. It is therefore important to ensure that these keys do not clash with any
        // resources created in any other place that uses the same coordinator.
        private const val CONFIG_HANDLE = "RegistrationServiceLifecycleHandler.CONFIG_HANDLE"
        private const val COMPONENT_HANDLE = "RegistrationServiceLifecycleHandler.COMPONENT_HANDLE"
    }

    private val publisherFactory = staticMemberRegistrationService.publisherFactory

    private val configurationReadService = staticMemberRegistrationService.configurationReadService

    private var _publisher: Publisher? = null

    /**
     * Publisher for Kafka messaging. Recreated after every [MESSAGING_CONFIG] change.
     */
    val publisher: Publisher
        get() = _publisher ?: throw IllegalArgumentException("Publisher is not initialized.")

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> handleStartEvent(coordinator)
            is StopEvent -> handleStopEvent()
            is RegistrationStatusChangeEvent -> handleDependencyRegistrationChange(event, coordinator)
            is ConfigChangedEvent -> handleConfigChange(event, coordinator)
        }
    }

    private fun handleStartEvent(coordinator: LifecycleCoordinator) {
        coordinator.createManagedResource(COMPONENT_HANDLE) {
            coordinator.followStatusChangesByName(
                setOf(
                    LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
                    LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                    LifecycleCoordinatorName.forComponent<MembershipQueryClient>(),
                    LifecycleCoordinatorName.forComponent<MembershipPersistenceClient>(),
                    LifecycleCoordinatorName.forComponent<HSMRegistrationClient>(),
                )
            )
        }
    }

    private fun handleStopEvent() {
        _publisher?.close()
        _publisher = null
    }

    private fun handleDependencyRegistrationChange(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator
    ) {
        when (event.status) {
            LifecycleStatus.UP -> {
                coordinator.createManagedResource(CONFIG_HANDLE) {
                    configurationReadService.registerComponentForUpdates(
                        coordinator,
                        setOf(BOOT_CONFIG, MESSAGING_CONFIG)
                    )
                }
            }
            else -> {
                coordinator.updateStatus(LifecycleStatus.DOWN)
                coordinator.closeManagedResources(setOf(CONFIG_HANDLE))
            }
        }
    }

    // re-creates the publisher with the new config, sets the lifecycle status to UP when the publisher is ready for the first time
    private fun handleConfigChange(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        _publisher?.close()
        _publisher = publisherFactory.createPublisher(
            PublisherConfig("static-member-registration-service"),
            event.config.getConfig(MESSAGING_CONFIG)
        )
        _publisher?.start()

        coordinator.updateStatus(LifecycleStatus.UP)
    }
}