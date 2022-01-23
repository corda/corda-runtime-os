package net.corda.crypto.impl

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import java.util.concurrent.ConcurrentHashMap

class LifecycleDependenciesTracker(
    private val owner: LifecycleCoordinator,
    dependencies: Set<LifecycleCoordinatorName>
) : AutoCloseable {
    companion object {
        private val logger = contextLogger()

        fun LifecycleCoordinator.track(vararg dependencies: LifecycleCoordinatorName) =
            LifecycleDependenciesTracker(this, dependencies.toSet())

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

    override fun close() {
        registrations.forEach { it.key.close() }
        registrations.clear()
    }

    fun areUpAfter(event: RegistrationStatusChangeEvent, coordinator: LifecycleCoordinator): Boolean {
        processEvent(event, coordinator)
        return areUp()
    }

    fun processEvent(event: RegistrationStatusChangeEvent, coordinator: LifecycleCoordinator) {
        logger.info("{}: processing {} from {}", owner.name, event, coordinator.name)
        registrations.computeIfPresent(event.registration) { _, _ ->
            StatusHolder(event.status)
        }
    }

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