package net.corda.crypto.component.impl

import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle

interface DependenciesTracker {
    val dependencies: Set<LifecycleCoordinatorName>
    val isUp: Boolean
    fun clear()

    open class Default(
        override val dependencies: Set<LifecycleCoordinatorName>
    ) : DependenciesTracker {
        @Volatile
        private var handle: RegistrationHandle? = null

        @Volatile
        private var status: LifecycleStatus = LifecycleStatus.DOWN

        override val isUp: Boolean get() = status == LifecycleStatus.UP

        override fun clear() {
            handle?.close()
            handle = null
            status = LifecycleStatus.DOWN
        }
    }

    class AlwaysUp : DependenciesTracker {
        override val dependencies: Set<LifecycleCoordinatorName> = emptySet()

        override val isUp: Boolean = true

        override fun clear() = Unit
    }
}
