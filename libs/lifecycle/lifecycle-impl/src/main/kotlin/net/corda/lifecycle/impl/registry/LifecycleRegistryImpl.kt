package net.corda.lifecycle.impl.registry

import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.impl.LifecycleCoordinatorInternal
import net.corda.lifecycle.registry.CoordinatorStatus
import net.corda.lifecycle.registry.LifecycleRegistry
import net.corda.lifecycle.registry.LifecycleRegistryException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.trace
import org.osgi.service.component.annotations.Component
import java.util.concurrent.ConcurrentHashMap

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

        private val logger = contextLogger()
    }

    private val coordinators: MutableMap<LifecycleCoordinatorName, LifecycleCoordinatorInternal> =
        ConcurrentHashMap()

    private val statuses: MutableMap<LifecycleCoordinatorName, CoordinatorStatus> = ConcurrentHashMap()

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
            val coordinatorStatus = CoordinatorStatus(name, status, reason)
            statuses[name] = coordinatorStatus
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
        logger.info("Removing coordinator $name from registry")
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
