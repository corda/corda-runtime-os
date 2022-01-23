package net.corda.lifecycle

import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import java.util.concurrent.ConcurrentHashMap

/**
 * Encapsulates a group of Dependent components which UP status should be tracked.
 *
 * @constructor Create object which can track dependencies UP status
 *
 * @throws [IllegalArgumentException] if the list of dependencies is empty set.
 *
 * @sample
 * private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
 *  when (event) {
 *      is StartEvent -> {
 *          tracker?.close()
 *          tracker = lifecycleCoordinator.track(SoftKeysPersistenceProvider::class.java)
 *      }
 *      is StopEvent -> {
 *          tracker?.close()
 *          tracker = null
 *      }
 *      is RegistrationStatusChangeEvent -> {
 *          if (tracker?.areUpAfter(event, coordinator) == true) {
 *              this.lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
 *          } else {
 *              this.lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
 *      }
 *  }
 *  else -> logger.error("Unexpected event $event!")
 * }
 *}
 */
class LifecycleDependenciesTracker(
    private val owner: LifecycleCoordinator,
    dependencies: Set<LifecycleCoordinatorName>
) : AutoCloseable {
    companion object {
        private val logger = contextLogger()

        /**
         * Instantiates [LifecycleDependenciesTracker] for a given set of [LifecycleCoordinatorName]
         */
        fun LifecycleCoordinator.track(vararg dependencies: LifecycleCoordinatorName) =
            LifecycleDependenciesTracker(this, dependencies.toSet())

        /**
         * Instantiates [LifecycleDependenciesTracker] for a given set of dependencies implementing [Lifecycle],
         * uses LifecycleCoordinatorName(it.name) under the hood
         */
        fun LifecycleCoordinator.track(vararg dependencies: Class<out Lifecycle>) =
            LifecycleDependenciesTracker(this, dependencies.map {
                LifecycleCoordinatorName(it.name)
            }.toSet())
    }

    private val registrations: ConcurrentHashMap<RegistrationHandle, StatusHolder>

    init {
        require(dependencies.isNotEmpty()) {
            "There should be at least one dependency."
        }
        val map = dependencies.associate {
            owner.followStatusChangesByName(setOf(it)) to StatusHolder(null)
        }
        registrations = ConcurrentHashMap(map)
        logger.debug { "${owner.name}: follows status of [${dependencies.joinToString()}]" }
    }

    /**
     * Releases all follow status registrations
     */
    override fun close() {
        registrations.forEach { it.key.close() }
        registrations.clear()
    }

    /**
     * Processes [RegistrationStatusChangeEvent] event and checks whenever all dependencies have UP status.
     *
     * @return true if all dependencies are UP
     */
    fun areUpAfter(event: RegistrationStatusChangeEvent, coordinator: LifecycleCoordinator): Boolean {
        processEvent(event, coordinator)
        return areUp()
    }

    /**
     * Processes [RegistrationStatusChangeEvent] event.
     */
    fun processEvent(event: RegistrationStatusChangeEvent, coordinator: LifecycleCoordinator) {
        logger.info("{}: processing {} from {}", owner.name, event, coordinator.name)
        registrations.computeIfPresent(event.registration) { _, _ ->
            StatusHolder(event.status)
        }
    }

    /**
     * Returns true if all dependencies are UP
     */
    fun areUp(): Boolean {
        val upCount = upCount()
        logger.debug { "${owner.name}: up $upCount out of ${registrations.size}" }
        return upCount == registrations.size
    }

    private fun upCount(): Int =
        registrations.count { it.value.status == LifecycleStatus.UP }

    class StatusHolder(
        val status: LifecycleStatus?
    )
}