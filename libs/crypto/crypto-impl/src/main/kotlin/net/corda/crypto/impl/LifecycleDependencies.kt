package net.corda.crypto.impl

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.ConcurrentHashMap

class LifecycleDependencies(
    private val owner: Class<*>,
    coordinator: LifecycleCoordinator,
    vararg components: Class<*>
) : AutoCloseable {
    companion object {
        private val logger = contextLogger()
    }

    private val registrations: ConcurrentHashMap<RegistrationHandle, StatusHolder>

    init {
        val map = components.associate {
            val name = LifecycleCoordinatorName(it.name)
            logger.debug("{} following status changes for name: {}", owner.name, name)
            coordinator.followStatusChangesByName(setOf(name)) to StatusHolder(null)
        }
        registrations = ConcurrentHashMap(map)
    }

    fun areUpAfter(event: RegistrationStatusChangeEvent): Boolean {
        processEvent(event)
        logger.debug("{} processed: {}, up {} out of {}", owner.name, event,  upCount(), registrations.size)
        return areUp()
    }

    override fun close() {
        registrations.forEach { it.key.closeGracefully() }
        registrations.clear()
    }

    private fun processEvent(event: RegistrationStatusChangeEvent) {
        registrations.computeIfPresent(event.registration) { _, _ ->
            StatusHolder(event.status)
        }
    }

    private fun areUp(): Boolean =
        registrations.isNotEmpty() && upCount() == registrations.size

    private fun upCount(): Int =
        registrations.count { it.value.status == LifecycleStatus.UP }

    class StatusHolder(
        val status: LifecycleStatus?
    )
}