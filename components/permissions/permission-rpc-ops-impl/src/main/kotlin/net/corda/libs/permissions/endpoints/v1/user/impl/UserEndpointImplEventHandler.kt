package net.corda.libs.permissions.endpoints.v1.user.impl

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.permissions.service.PermissionServiceComponent
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug

internal class UserEndpointImplEventHandler : LifecycleEventHandler {

    private companion object {
        val logger = contextLogger()
    }

    @VisibleForTesting
    internal var registration: RegistrationHandle? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.debug { "Received event: $event" }
        when (event) {
            is StartEvent -> {
                followServicesForStatusUpdates(coordinator)
            }
            is RegistrationStatusChangeEvent -> {
                coordinator.updateStatus(event.status)
            }
            is StopEvent -> {
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
                LifecycleCoordinatorName.forComponent<PermissionServiceComponent>()
            )
        )
    }
}