package net.corda.crypto.client.impl

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class AbstractComponent<RESOURCE: AutoCloseable>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    coordinatorName: LifecycleCoordinatorName
) : Lifecycle {
    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator(coordinatorName, ::eventHandler)

    @Volatile
    var resources: RESOURCE? = null
        private set

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        logger.info("Starting...")
        lifecycleCoordinator.start()
        lifecycleCoordinator.postEvent(StartEvent())
    }

    override fun stop() {
        logger.info("Stopping...")
        lifecycleCoordinator.postEvent(StopEvent())
        lifecycleCoordinator.stop()
    }

    protected open fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("LifecycleEvent received: $event")
        when (event) {
            is StartEvent -> {
                createResources()
                setStatusUp()
            }
            is StopEvent -> {
                deleteResources()
            }
            else -> {
                logger.error("Unexpected event $event!")
            }
        }
    }

    protected fun createResources() {
        logger.info("Creating resources")
        val tmp = resources
        resources = allocateResources()
        tmp?.close()
    }

    protected fun deleteResources() {
        logger.info("Closing resources")
        val tmp = resources
        resources = null
        tmp?.close()
    }

    protected abstract fun allocateResources(): RESOURCE

    private fun setStatusUp() {
        logger.info("Setting status UP.")
        lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
    }
}