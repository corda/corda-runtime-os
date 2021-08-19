package net.corda.lifecycle.registry.impl

import net.corda.lifecycle.registry.CoordinatorStatus
import net.corda.lifecycle.registry.StatusChangeEventHandler
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A representation of a registered callback with the lifecycle registry.
 *
 * Can be closed, which signals to the registry that this callback should be unregistered.
 *
 * @param callback The callback method registered by the client code.
 * @param registry The lifecycle registry.
 */
class CallbackRegistration(
    private val callback: StatusChangeEventHandler,
    private val registry: LifecycleRegistryImpl
) : AutoCloseable {

    private val isClosed = AtomicBoolean(false)

    /**
     * Invoke the provided callback if the registration has not been closed.
     *
     * This allows the callback to be unregistered effective immediately, while state in the registry can be cleaned up
     * in the background.
     *
     * @param statuses The current statuses of the registered coordinators.
     * @param statusChange The coordinator status that has changed.
     */
    fun invokeCallback(statuses: Map<String, CoordinatorStatus>, statusChange: CoordinatorStatus) {
        if (!isClosed.get()) {
            callback.onStatusChange(statuses, statusChange)
        }
    }

    /**
     * Cancel this registration.
     *
     * This immediately prevents the callback from being invoked, while triggering a message for state in the registry
     * to be cleaned up in the background.
     */
    override fun close() {
        if (!isClosed.getAndSet(true)) {
            registry.unregisterCallback(this)
        }
    }
}