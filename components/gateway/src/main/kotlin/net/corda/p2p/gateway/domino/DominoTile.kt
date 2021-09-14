package net.corda.p2p.gateway.domino

import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedDeque

abstract class DominoTile(
    internal val coordinatorFactory: DominoCoordinatorFactory,
) : Lifecycle, LifecycleEventHandler {
    companion object {
        private val logger = contextLogger()
    }
    private val coordinator by lazy {
        coordinatorFactory.createFor(this)
    }

    private val createdResources = ConcurrentLinkedDeque<AutoCloseable>()

    private val statusChangeLock = CompletableFuture<LifecycleStatus>()

    private fun updateStatus(newStatus: LifecycleStatus) {
        coordinator.updateStatus(newStatus)
        statusChangeLock.complete(newStatus)
    }

    abstract fun prepareResources()

    fun keepResource(resource: AutoCloseable) {
        createdResources.addFirst(resource)
    }

    override val isRunning
        get() = coordinator.status == LifecycleStatus.UP

    override fun start() {
        logger.info("Starting ${coordinator.name}")
        coordinator.start()

        if (statusChangeLock.get() != LifecycleStatus.UP) {
            throw DominoException("Can not start ${coordinator.name}")
        }
        logger.info("${coordinator.name} started")
    }

    override fun stop() {
        logger.info("Stopping ${coordinator.name}")
        coordinator.stop()
    }

    fun gotError(error: Throwable) {
        coordinator.postEvent(ErrorEvent(error))
    }

    private fun handleErrorEvent(event: ErrorEvent) {
        logger.warn("Got error", event.cause)
        updateStatus(LifecycleStatus.ERROR)
        stop()
    }
    private fun handleStartEvent() {
        prepareResources()
        createdResources
            .reversed()
            .filterIsInstance<Lifecycle>()
            .forEach {
                it.start()
            }

        val otherCoordinators = createdResources
            .filterIsInstance<DominoTile>()
            .map { it.coordinator }
            .toSet()
        if (otherCoordinators.isEmpty()) {
            updateStatus(LifecycleStatus.UP)
        } else {
            val handler = coordinator.followStatusChanges(otherCoordinators)
            createdResources.add(handler)
        }
    }

    private fun handleStopEvent() {
        createdResources
            .forEach {
                try {
                    if (it is Lifecycle) {
                        it.stop()
                    } else {
                        it.close()
                    }
                } catch (e: Throwable) {
                    logger.warn("Can not close $it", e)
                }
            }
        updateStatus(LifecycleStatus.DOWN)
        createdResources.clear()
    }

    private fun handleChildStatusChange(newStatus: LifecycleStatus) {
        if (newStatus != LifecycleStatus.UP) {
            stop()
        } else {
            if (createdResources.isNotEmpty()) {
                if (createdResources.filterIsInstance<Lifecycle>()
                    .all { it.isRunning }
                ) {
                    updateStatus(LifecycleStatus.UP)
                }
            }
        }
    }

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is ErrorEvent -> {
                handleErrorEvent(event)
            }
            is StartEvent -> {
                handleStartEvent()
            }
            is StopEvent -> {
                handleStopEvent()
            }
            is RegistrationStatusChangeEvent -> {
                handleChildStatusChange(event.status)
            }
            else -> {
                logger.warn("Unknown event", event)
            }
        }
    }
}
