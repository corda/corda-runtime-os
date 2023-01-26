package net.corda.lifecycle.impl

import net.corda.lifecycle.CustomEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

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
    private val coordinators: Set<LifecycleCoordinatorInternal>,
    private val registeringCoordinator: LifecycleCoordinatorInternal
) : RegistrationHandle {

    private  companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val coordinatorStatusMap = ConcurrentHashMap(coordinators.associateWith { LifecycleStatus.DOWN })

    private val isClosed = AtomicBoolean(false)

    private val currentStatus: LifecycleStatus
        get() = if (coordinatorStatusMap.values.all { it == LifecycleStatus.UP }) {
            LifecycleStatus.UP
        } else {
            LifecycleStatus.DOWN
        }

    /**
     * Used to synchronize calls to registration status updates. Race conditions could result in status change events
     * being delivered out of order (which could result in a hang) or status updates not being delivered at all.
     */
    private val lock = ReentrantLock()


    /**
     * Update this registration with the status of one of the coordinators.
     *
     * This also triggers a [RegistrationStatusChangeEvent] to be posted if the overall registration status changes as
     * a result of this update.
     *
     * This must be synchronized as it is possible for two coordinators to change status at about the same time, which
     * could result in incorrect status changes being posted.
     *
     * @param coordinator The coordinator status that has changed.
     * @param status The new status of the coordinator.
     */
    fun updateCoordinatorStatus(coordinator: LifecycleCoordinatorInternal, status: LifecycleStatus) {
        lock.withLock {
            val oldState = currentStatus
            coordinatorStatusMap[coordinator] = status
            val newState = currentStatus
            if (!isClosed.get() && oldState != newState) {
                val message = "Coordinator ${registeringCoordinator.name} received RegistrationStatusChangeEvent $newState due to " +
                        "${coordinator.name} changing to state $status"
                if (newState == LifecycleStatus.ERROR) { logger.warn(message) } else { logger.info(message) }
                registeringCoordinator.postEvent(RegistrationStatusChangeEvent(this, newState))
            }
        }
    }

    /**
     * Sends a custom event to this registration.
     */
    fun postCustomEvent(event: CustomEvent) {
        lock.withLock {
            if (!isClosed.get()) {
                registeringCoordinator.postEvent(event)
            }
        }
    }

    /**
     * Notify the registering coordinator of the current status of this registration, if it is up.
     *
     * This is primarily useful for ensuring that an UP event is delivered as the coordinator goes up.
     *
     * Must be synchronized with [updateCoordinatorStatus] to counter the race condition of the dependent coordinators
     * updating the status at the same time as the registering coordinator makes this request.
     */
    fun notifyCurrentStatus() {
        lock.withLock {
            val status = currentStatus
            if (!isClosed.get() && status == LifecycleStatus.UP) {
                registeringCoordinator.postEvent(RegistrationStatusChangeEvent(this, status))
            }
        }
    }

    /**
     * Cancel the registration. On cancellation no further status updates are delivered, and the state on the
     * coordinators on both sides is cleaned up.
     */
    override fun close() {
        if (!isClosed.getAndSet(true)) {
            registeringCoordinator.postInternalEvent(StopTrackingRegistration(this))
            coordinators.forEach { coordinator ->
                coordinator.postInternalEvent(CancelRegistration(this))
            }
        }
    }

    override fun toString(): String {
        return "Registration(registeringCoordinator=${registeringCoordinator.name}," +
                "coordinators=${coordinators.map { it.name }.joinToString()})"
    }
}
