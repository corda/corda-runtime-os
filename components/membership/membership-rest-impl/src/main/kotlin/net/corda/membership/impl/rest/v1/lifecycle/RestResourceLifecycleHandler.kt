package net.corda.membership.impl.rest.v1.lifecycle

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent

class RestResourceLifecycleHandler(
    val activate: (String) -> Unit,
    val deactivate: (String) -> Unit,
    private val dependencies: Set<LifecycleCoordinatorName>
) : LifecycleEventHandler {
    // for checking the components' health
    private var componentHandle: AutoCloseable? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when(event) {
            is StartEvent -> {
                componentHandle?.close()
                componentHandle = coordinator.followStatusChangesByName(dependencies)
            }
            is StopEvent -> {
                componentHandle?.close()
                deactivate.invoke("Stopping component")
            }
            is RegistrationStatusChangeEvent -> {
                when (event.status) {
                    LifecycleStatus.UP -> {
                        activate.invoke("Dependencies are UP")
                    }
                    else -> {
                        deactivate.invoke("Dependencies are DOWN")
                    }
                }
            }
        }
    }
}