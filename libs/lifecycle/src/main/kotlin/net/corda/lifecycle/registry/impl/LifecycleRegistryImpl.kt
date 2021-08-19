package net.corda.lifecycle.registry.impl

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.registry.CoordinatorStatus
import net.corda.lifecycle.registry.LifecycleRegistry
import net.corda.lifecycle.registry.LifecycleRegistryException
import net.corda.lifecycle.registry.StatusChangeEventHandler
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.trace
import org.osgi.service.component.annotations.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * The lifecycle registry implementation.
 *
 * The lifecycle registry provides a name -> coordinator mapping that can be used to lookup coordinator instances. This
 * part of the API must be responsive - i.e. there shouldn't be a delay between the coordinator first becoming known to
 * the registry and it being available in the mapping. This is therefore handled with a simple concurrent map.
 *
 * The second part of the API is a mechanism for monitoring components to learn about the current status of coordinators
 * in the process, as well as any status updates. For this some care is required, as status updates are likely to happen
 * concurrently across many threads. The lifecycle registry uses a separate status manager to handle this, which follows
 * a similar actor model to the coordinators themselves. This ensures that updates arrive sequentially, while also
 * preventing any lifecycle threads being blocked on status update callbacks being invoked.
 */
@Component(service = [LifecycleRegistry::class])
class LifecycleRegistryImpl(private val statusManager: LifecycleStatusManager) : LifecycleRegistry,
    LifecycleRegistryCoordinatorAccess {

    private companion object {
        private const val NEW_COORDINATOR_REASON = "Coordinator has just been created"

        private val logger = contextLogger()
    }

    private val coordinators: MutableMap<String, LifecycleCoordinator> = ConcurrentHashMap()

    /**
     * See [LifecycleRegistryCoordinatorAccess].
     */
    override fun updateStatus(name: String, status: LifecycleStatus, reason: String) {
        val coordinatorStatus = CoordinatorStatus(name, status, reason)
        statusManager.postEvent(RegistryEvent.UpdateStatus(coordinatorStatus))
        logger.trace { "Coordinator status update: $name is now $status ($reason)" }
    }

    /**
     * See [LifecycleRegistryCoordinatorAccess].
     */
    override fun registerCoordinator(name: String, coordinator: LifecycleCoordinator) {
        val coordinatorStatus = CoordinatorStatus(name, LifecycleStatus.DOWN, NEW_COORDINATOR_REASON)
        coordinators[name] = coordinator
        statusManager.postEvent(RegistryEvent.UpdateStatus(coordinatorStatus))
        logger.trace { "Registered new coordinator with name $name" }
    }

    /**
     * See [LifecycleRegistryCoordinatorAccess].
     */
    override fun getCoordinator(name: String): LifecycleCoordinator {
        return coordinators[name]
            ?: throw LifecycleRegistryException("No coordinator with name $name has been registered")
    }

    /**
     * See [LifecycleRegistry].
     */
    override fun componentStatus(): Map<String, CoordinatorStatus> {
        return statusManager.getStatuses()
    }

    /**
     * See [LifecycleRegistry].
     */
    override fun registerForStatusChanges(eventHandler: StatusChangeEventHandler): AutoCloseable {
        val callback = CallbackRegistration(eventHandler, this)
        statusManager.postEvent(RegistryEvent.NewCallback(callback))
        return callback
    }

    /**
     * Unregister the provided callback.
     *
     * Used by the callback registration when the registration is closed.
     */
    fun unregisterCallback(callback: CallbackRegistration) {
        statusManager.postEvent(RegistryEvent.CancelCallback(callback))
    }
}