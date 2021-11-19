package net.corda.permissions.rpcops.internal

import net.corda.libs.permissions.endpoints.v1.user.impl.UserEndpointImpl
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.permissions.service.PermissionService
import net.corda.v5.base.annotations.VisibleForTesting

internal class PermissionRpcOpsServiceEventHandler(
    private val permissionService: PermissionService
) : LifecycleEventHandler {

    internal var userEndpoint: UserEndpointImpl? = null

    @VisibleForTesting
    internal var registration: RegistrationHandle? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                registration?.close()
                registration = coordinator.followStatusChangesByName(
                    setOf(LifecycleCoordinatorName.forComponent<PermissionService>())
                )
            }
            is RegistrationStatusChangeEvent -> {
                when (event.status) {
                    LifecycleStatus.UP -> {
                        startEndpoints()
                        coordinator.updateStatus(LifecycleStatus.UP)
                    }
                    else -> {
                        coordinator.updateStatus(event.status)
                    }
                }
            }
            is StopEvent -> {
                registration?.close()
                registration = null
                userEndpoint?.stop()
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
        }
    }

    private fun startEndpoints() {
        if(userEndpoint == null){
            userEndpoint = UserEndpointImpl(permissionService.permissionManager, permissionService.permissionValidator)
        } else {
            // Maintaining an endpoint with manager and validator being null allows us to keep servicing requests
            userEndpoint!!.setPermissionManager(permissionService.permissionManager)
            userEndpoint!!.setPermissionValidator(permissionService.permissionValidator)
        }

        userEndpoint!!.start()
    }
}