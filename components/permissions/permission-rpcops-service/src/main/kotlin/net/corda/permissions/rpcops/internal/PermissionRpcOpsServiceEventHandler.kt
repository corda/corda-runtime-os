package net.corda.permissions.rpcops.internal

import net.corda.libs.permissions.endpoints.v1.user.UserEndpoint
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

internal class PermissionRpcOpsServiceEventHandler(
    private val permissionServiceComponent: PermissionServiceComponent,
    private val userEndpoint: UserEndpoint
) : LifecycleEventHandler {

    @VisibleForTesting
    internal var registration: RegistrationHandle? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                registration?.close()
                registration = coordinator.followStatusChangesByName(
                    setOf(LifecycleCoordinatorName.forComponent<PermissionServiceComponent>())
                )
            }
            is RegistrationStatusChangeEvent -> {
                when (event.status) {
                    LifecycleStatus.UP -> {
                        startEndpoints()
                        coordinator.updateStatus(LifecycleStatus.UP)
                    }
                    LifecycleStatus.DOWN -> {
                        userEndpoint.permissionValidator = null
                        userEndpoint.permissionManager = null
                        coordinator.updateStatus(LifecycleStatus.DOWN)
                    }
                    LifecycleStatus.ERROR -> {
                        userEndpoint.stop()
                        coordinator.updateStatus(LifecycleStatus.ERROR)
                    }
                }
            }
            is StopEvent -> {
                registration?.close()
                registration = null
                userEndpoint.stop()
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
        }
    }

    private fun startEndpoints() {
        userEndpoint.permissionManager = permissionServiceComponent.permissionManager
        userEndpoint.permissionValidator = permissionServiceComponent.permissionValidator
        userEndpoint.start()
    }
}