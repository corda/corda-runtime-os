package net.corda.lifecycle.impl

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A registration of a parent coordinator to a set of underlying child coordinators.
 *
 * On registration, an instance of this is returned as the registration handle and the registration is handed to each
 * of the child coordinators. When these update their status, the registration is also updated. Overall status changes
 * are only delivered on change. The overall status is considered to be up only if all the underlying statuses are also
 * up.
 *
 * The registration must be thread safe in its update and close functions, as it is very likely that the update function
 * is called from multiple threads simultaneously.
 *
 * @param coordinators The set of coordinators that has been registered to.
 * @param registeringCoordinator The coordinator to deliver status updates to.
 */
internal class Registration(
    private val coordinators: Set<LifecycleCoordinator>,
    private val registeringCoordinator: LifecycleCoordinator
) : RegistrationHandle {

    private val coordinatorStatusMap = ConcurrentHashMap(coordinators.associateWith { LifecycleStatus.DOWN })

    private val isClosed = AtomicBoolean(false)

    private val currentStatus: LifecycleStatus
        get() = if (coordinatorStatusMap.values.any { it == LifecycleStatus.DOWN }) {
            LifecycleStatus.DOWN
        } else {
            LifecycleStatus.UP
        }


    @Synchronized
    fun updateCoordinatorState(coordinator: LifecycleCoordinator, state: LifecycleStatus) {
        val oldState = currentStatus
        coordinatorStatusMap[coordinator] = state
        val newState = currentStatus
        if (!isClosed.get() && oldState != newState) {
            registeringCoordinator.postEvent(RegistrationStatusChangeEvent(this, newState))
        }
    }

    override fun close() {
        if (!isClosed.getAndSet(true)) {
            coordinators.forEach { it.postEvent(CancelRegistration(this)) }
        }
    }
}