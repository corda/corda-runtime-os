package net.corda.libs.permissions.endpoints.common

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.permissions.management.PermissionManagementService
import net.corda.utilities.VisibleForTesting
import net.corda.utilities.debug
import org.slf4j.LoggerFactory

internal class PermissionEndpointEventHandler(private val endpointName: String) : LifecycleEventHandler {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @VisibleForTesting
    internal var registration: RegistrationHandle? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.debug { "${endpointName}: Received event: $event" }
        when (event) {
            is StartEvent -> {
                log.info("${endpointName}: Received start event, following PermissionServiceComponent for status updates.")
                followServicesForStatusUpdates(coordinator)
            }
            is RegistrationStatusChangeEvent -> {
                log.info("${endpointName}: Received status update from PermissionServiceComponent: ${event.status}.")
                coordinator.updateStatus(event.status)
            }
            is StopEvent -> {
                log.info("${endpointName}: Received stop event, closing dependencies and setting status to DOWN.")
                registration?.close()
                registration = null
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
        }
    }

    private fun followServicesForStatusUpdates(coordinator: LifecycleCoordinator) {
        registration?.close()

        registration = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<PermissionManagementService>()
            )
        )
    }
}