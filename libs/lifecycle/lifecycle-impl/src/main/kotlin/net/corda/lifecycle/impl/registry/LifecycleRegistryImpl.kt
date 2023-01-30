package net.corda.lifecycle.impl.registry

import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.impl.LifecycleCoordinatorInternal
import net.corda.lifecycle.registry.CoordinatorStatus
import net.corda.lifecycle.registry.LifecycleRegistry
import net.corda.lifecycle.registry.LifecycleRegistryException
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * The lifecycle registry implementation.
 *
 * The lifecycle registry provides two pieces of functionality. The first is a lookup mechanism for finding coordinators
 * based on a name. The second is a record of the current statuses of all coordinators in the system.
 */
@Component(service = [LifecycleRegistry::class, LifecycleRegistryCoordinatorAccess::class])
class LifecycleRegistryImpl : LifecycleRegistry, LifecycleRegistryCoordinatorAccess {

    private companion object {
        private const val NEW_COORDINATOR_REASON = "Coordinator has just been created"

        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val coordinators: MutableMap<LifecycleCoordinatorName, LifecycleCoordinatorInternal> =
        ConcurrentHashMap()

    private val statuses: ConcurrentMap<LifecycleCoordinatorName, CoordinatorStatus> = ConcurrentHashMap()

    /**
     * See [LifecycleRegistryCoordinatorAccess].
     */
    override fun updateStatus(name: LifecycleCoordinatorName, status: LifecycleStatus, reason: String) {
        if (statuses[name] == null) {
            logger.warn("Attempt was made to update the status of coordinator $name to $status " +
                    "($reason) that has not been registered with the registry.")
//            throw LifecycleRegistryException(
//                "Attempt was made to update the status of coordinator $name to $status " +
//                        "($reason) that has not been registered with the registry."
//            )
        } else {
            // Guards against `updateStatus` and `removeCoordinator` being called concurrently.
            // Without `computeIfPresent`, `updateStatus` may (in theory) re-introduce just removed coordinator to `statuses` map only,
            // but not to `coordinators` map.
            val coordinatorStatus = CoordinatorStatus(name, status, reason)
            statuses.computeIfPresent(name) { _, _ -> coordinatorStatus }
            logger.trace { "Coordinator status update: $name is now $status ($reason)" }
        }
    }

    /**
     * See [LifecycleRegistryCoordinatorAccess].
     */
    override fun registerCoordinator(name: LifecycleCoordinatorName, coordinator: LifecycleCoordinatorInternal) {
        val coordinatorStatus = CoordinatorStatus(name, LifecycleStatus.DOWN, NEW_COORDINATOR_REASON)
        val oldValue = coordinators.putIfAbsent(name, coordinator)
        if (oldValue != null) {
            throw LifecycleRegistryException("A coordinator with name $name has already been registered")
        }
        statuses[name] = coordinatorStatus
        logger.trace { "Registered new coordinator with name $name" }
    }

    /**
     * See [LifecycleRegistryCoordinatorAccess].
     */
    override fun getCoordinator(name: LifecycleCoordinatorName): LifecycleCoordinatorInternal {
        return coordinators[name]
            ?: throw LifecycleRegistryException("No coordinator with name $name has been registered")
    }

    /**
     * See [LifecycleRegistryCoordinatorAccess]
     */
    override fun removeCoordinator(name: LifecycleCoordinatorName) {
        logger.debug { "Removing coordinator $name from registry" }
        coordinators.remove(name)
        statuses.remove(name)
    }

    /**
     * See [LifecycleRegistry].
     */
    override fun componentStatus(): Map<LifecycleCoordinatorName, CoordinatorStatus> {
        return statuses.toMap()
    }
}
