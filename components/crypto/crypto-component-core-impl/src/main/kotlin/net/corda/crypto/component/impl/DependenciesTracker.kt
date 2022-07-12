package net.corda.crypto.component.impl

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

interface DependenciesTracker {
    val isUp: Boolean
    fun follow(coordinator: LifecycleCoordinator)
    fun clear()
    fun on(event: RegistrationStatusChangeEvent): EventHandling

    enum class EventHandling { HANDLED, UNHANDLED }

    open class Default(
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

    class AlwaysUp(
        coordinatorFactory: LifecycleCoordinatorFactory,
        impl: Any,
        implName: LifecycleCoordinatorName = LifecycleCoordinatorName(impl::class.java.name)
    ) : Default(setOf(implName)), Lifecycle {
        private val logger: Logger = LoggerFactory.getLogger(impl::class.java)

        private val lifecycleCoordinator = coordinatorFactory.createCoordinator(implName) { event, coordinator ->
            if(event is StartEvent) {
                coordinator.updateStatus(LifecycleStatus.UP)
            }
        }

        override val isRunning: Boolean
            get() = lifecycleCoordinator.isRunning

        override fun start() {
            logger.info("Starting...")
            lifecycleCoordinator.start()
        }

        override fun stop() {
            logger.info("Stopping...")
            lifecycleCoordinator.stop()
        }

        override fun close() {
            logger.info("Closing...")
            super.close()
            lifecycleCoordinator.close()
        }
    }
}
