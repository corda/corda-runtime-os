package net.corda.crypto.component.lifecycle

import net.corda.crypto.impl.closeGracefully
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class AbstractComponent<RESOURCE: AutoCloseable> : Lifecycle {
    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private lateinit var coordinator: LifecycleCoordinator

    fun setup(
        coordinatorFactory: LifecycleCoordinatorFactory,
        coordinatorName: LifecycleCoordinatorName
    ) {
        coordinator = coordinatorFactory.createCoordinator(coordinatorName) { event, _ ->
            handleCoordinatorEvent(event)
        }
    }

    @Volatile
    var resources: RESOURCE? = null
        private set

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        logger.info("Starting component")
        coordinator.start()
    }

    override fun stop() {
        logger.info("Stopping component")
        coordinator.stop()
        closeResources()
    }

    protected open fun handleCoordinatorEvent(event: LifecycleEvent) {
        logger.info("LifecycleEvent received: $event")
        when (event) {
            is RegistrationStatusChangeEvent -> {
                // No need to check what registration this is as there is only one.
                if (event.status == LifecycleStatus.UP) {
                    createResources()
                } else {
                    closeResources()
                }
            }
        }
    }

    protected fun createResources() {
        if (!readyCreateResources()) {
            return
        }
        logger.info("Creating resources")
        val tmp = resources
        resources = allocateResources()
        tmp?.closeGracefully()
    }

    protected fun closeResources() {
        logger.info("Closing resources")
        val tmp = resources
        resources = null
        tmp?.closeGracefully()
    }

    protected open fun readyCreateResources(): Boolean = isRunning

    protected abstract fun allocateResources(): RESOURCE
}