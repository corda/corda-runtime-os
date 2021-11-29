package net.corda.components.rpc.internal

import net.corda.lifecycle.Lifecycle
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

@Suppress("LongParameterList")
internal class HttpRpcGatewayEventHandler(
    private val permissionServiceComponent: PermissionServiceComponent,
    private val endpoints: List<Lifecycle>,
) : LifecycleEventHandler {

    private companion object {
        val log = contextLogger()
    }

    @VisibleForTesting
    internal var registration: RegistrationHandle? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                log.debug { "Starting HTTP RPC Gateway Component." }
                registration?.close()
                registration = coordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<PermissionServiceComponent>()
                    )
                )

                permissionServiceComponent.start()
                endpoints.forEach { it.start() }
            }
            is RegistrationStatusChangeEvent -> {
                log.debug { "HTTP RPC Gateway Component received status change: ${event.status}" }
                when (event.status) {
                    LifecycleStatus.UP -> {
                        coordinator.updateStatus(LifecycleStatus.UP)
                    }
                    LifecycleStatus.DOWN -> {
                        coordinator.updateStatus(LifecycleStatus.DOWN)
                    }
                    LifecycleStatus.ERROR -> {
                        coordinator.stop()
                        coordinator.updateStatus(LifecycleStatus.ERROR)
                    }
                }
            }
            is StopEvent -> {
                log.debug { "Stopping HTTP RPC Gateway Component." }
                registration?.close()
                registration = null
                permissionServiceComponent.stop()
                endpoints.forEach { it.stop() }
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
        }
    }
}