package net.corda.membership.impl.registration.proxy.lifecycle

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.registration.MemberRegistrationService
import net.corda.v5.base.util.contextLogger

class RegistrationProxyLifecycleHandler(
    private val registrationServices: List<MemberRegistrationService>
) : LifecycleEventHandler {
    companion object {
        val logger = contextLogger()

        const val UP_REASON_READY = "All dependencies for RegistrationProxy are up so component is ready."
        const val DOWN_REASON_STOPPED = "RegistrationProxy was stopped."
        const val DOWN_REASON_NOT_READY = "Dependencies of RegistrationProxy are down."
    }

    private val dependencies = setOf(LifecycleCoordinatorName.forComponent<GroupPolicyProvider>()) +
            registrationServices.map { it.lifecycleCoordinatorName }

    private var dependencyStatusChangeHandle: AutoCloseable? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                logger.info(
                    registrationServices
                        .joinToString(
                            prefix = "Loaded registration services: [",
                            postfix = "]",
                            transform = { it.javaClass.name }
                        )
                )
                registrationServices.forEach { it.start() }
                dependencyStatusChangeHandle?.close()
                dependencyStatusChangeHandle = coordinator.followStatusChangesByName(dependencies)
            }
            is StopEvent -> {
                coordinator.down(DOWN_REASON_STOPPED)
                dependencyStatusChangeHandle?.close()
            }
            is RegistrationStatusChangeEvent -> {
                when (event.status) {
                    LifecycleStatus.UP -> coordinator.up(UP_REASON_READY)
                    else -> coordinator.down(DOWN_REASON_NOT_READY)
                }

            }
        }
    }

    private fun LifecycleCoordinator.up(reason: String) = changeStatus(LifecycleStatus.UP, reason)
    private fun LifecycleCoordinator.down(reason: String) = changeStatus(LifecycleStatus.DOWN, reason)
    private fun LifecycleCoordinator.changeStatus(status: LifecycleStatus, reason: String) {
        logger.debug(reason)
        updateStatus(status, reason)
    }
}
