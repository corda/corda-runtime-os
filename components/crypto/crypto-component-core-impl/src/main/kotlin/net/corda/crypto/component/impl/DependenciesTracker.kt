package net.corda.crypto.component.impl

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent

interface DependenciesTracker {
    val isUp: Boolean
    fun follow(coordinator: LifecycleCoordinator)
    fun clear()
    fun on(event: RegistrationStatusChangeEvent): EventHandling

    enum class EventHandling { HANDLED, UNHANDLED }

    class Default(
        private val dependencies: Set<LifecycleCoordinatorName>
    ) : DependenciesTracker {
        @Volatile
        private var handle: RegistrationHandle? = null

        @Volatile
        private var status: LifecycleStatus = LifecycleStatus.DOWN

        override val isUp: Boolean get() = status == LifecycleStatus.UP

        override fun follow(coordinator: LifecycleCoordinator) {
            clear()
            handle = coordinator.followStatusChangesByName(dependencies)
        }

        override fun clear() {
            handle?.close()
            handle = null
            status = LifecycleStatus.DOWN
        }

        override fun on(event: RegistrationStatusChangeEvent): EventHandling =
            if (event.registration == handle) {
                status = event.status
                EventHandling.HANDLED
            } else {
                EventHandling.UNHANDLED
        }
    }

    class AlwaysUp : DependenciesTracker {
        override val isUp: Boolean = true

        override fun follow(coordinator: LifecycleCoordinator) {
        }

        override fun clear() {
        }

        override fun on(event: RegistrationStatusChangeEvent): EventHandling = EventHandling.UNHANDLED
    }
}
