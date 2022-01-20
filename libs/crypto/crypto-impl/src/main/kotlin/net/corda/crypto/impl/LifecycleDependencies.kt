package net.corda.crypto.impl

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import java.util.concurrent.ConcurrentHashMap

class LifecycleDependencies(
    coordinator: LifecycleCoordinator,
    vararg components: Class<*>
) : AutoCloseable {
    private val registrations: ConcurrentHashMap<RegistrationHandle, LifecycleStatus?>

    init {
        registrations = ConcurrentHashMap(components.associate {
            coordinator.followStatusChangesByName(
                setOf(LifecycleCoordinatorName(it.name))
            ) to null
        })
    }

    fun processEvent(event: RegistrationStatusChangeEvent) {
        registrations.computeIfPresent(event.registration) { _, _ ->
            event.status
        }
    }

    fun areUpAfter(event: RegistrationStatusChangeEvent): Boolean {
        processEvent(event)
        return areUp()
    }

    fun areUp(): Boolean =
        registrations.isNotEmpty() && registrations.all { it.value == LifecycleStatus.UP }

    override fun close() {
        registrations.forEach { it.key.closeGracefully() }
        registrations.clear()
    }
}