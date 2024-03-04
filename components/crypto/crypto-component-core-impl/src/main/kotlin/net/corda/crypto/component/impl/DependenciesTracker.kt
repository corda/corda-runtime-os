package net.corda.crypto.component.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent

interface DependenciesTracker {
    val dependencies: Set<LifecycleCoordinatorName>
    val isUp: Boolean
    fun follow(coordinator: LifecycleCoordinator)
    fun clear()
    fun handle(event: RegistrationStatusChangeEvent): EventHandling

    enum class EventHandling { HANDLED, UNHANDLED }

    open class Default(
        override val dependencies: Set<LifecycleCoordinatorName>
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

        override fun handle(event: RegistrationStatusChangeEvent): EventHandling =
            if (event.registration == handle) {
                status = event.status
                EventHandling.HANDLED
            } else {
                EventHandling.UNHANDLED
            }
    }

    class AlwaysUp : DependenciesTracker {
        override val dependencies: Set<LifecycleCoordinatorName> = emptySet()

        override val isUp: Boolean = true

        override fun follow(coordinator: LifecycleCoordinator) = Unit

        override fun clear() = Unit

        override fun handle(event: RegistrationStatusChangeEvent): EventHandling =
            EventHandling.UNHANDLED
    }
}
