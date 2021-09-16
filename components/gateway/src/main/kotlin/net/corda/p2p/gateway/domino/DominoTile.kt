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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

// YIFT: Need to break this into more than one class and remove the suppression
@Suppress("TooManyFunctions")
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

    private val waitForStateChange = ConcurrentHashMap.newKeySet<CompletableFuture<LifecycleStatus>>()

    fun startAndWaitForStarted() {
        start()
        waitForStatus(LifecycleStatus.UP)
    }

    fun stopAndWaitForDestruction() {
        stop()
        waitForStatus(LifecycleStatus.DOWN)
    }

    private fun waitForStatus(statusToWait: LifecycleStatus) {
        val future = CompletableFuture<LifecycleStatus>()
        waitForStateChange.add(future)
        try {
            if (coordinator.status == statusToWait) {
                return
            }
            val newStatus = future.get()
            if (newStatus != statusToWait) {
                throw DominoException("Failed to wait for $statusToWait, status changed to $newStatus instead")
            }
        } finally {
            waitForStateChange.remove(future)
        }
    }

    abstract fun prepareResources()

    fun keepResources(vararg resources: Any) {
        resources.forEach {
            createdResources.addFirst(closeable(it))
        }
    }

    override val isRunning
        get() = coordinator.status == LifecycleStatus.UP

    override fun start() {
        logger.info("Starting ${coordinator.name}")
        coordinator.start()
    }

    override fun stop() {
        logger.info("Stopping ${coordinator.name}")
        coordinator.stop()
    }

    fun gotError(error: Throwable) {
        coordinator.postEvent(ErrorEvent(error))
        waitForStateChange.forEach {
            it.completeExceptionally(error)
        }
    }

    private fun handleErrorEvent(event: ErrorEvent) {
        logger.warn("Got error for ${coordinator.name}", event.cause)
        coordinator.updateStatus(LifecycleStatus.ERROR)
        stop()
    }

    protected open fun startupSequenceCompleted() {
        setReady()
    }

    protected fun setReady() {
        coordinator.updateStatus(LifecycleStatus.UP)
        logger.info("Started ${coordinator.name}")
        waitForStateChange.forEach {
            it.complete(LifecycleStatus.UP)
        }
    }

    private fun startNextResource() {
        val resourceNotStarted = createdResources
            .filterIsInstance<Lifecycle>()
            .filterNot {
                it.isRunning
            }.lastOrNull()

        if (resourceNotStarted == null) {
            startupSequenceCompleted()
        } else {
            resourceNotStarted.start()
            if (resourceNotStarted is DominoTile) {
                keepResources(
                    coordinator.followStatusChanges(
                        setOf(resourceNotStarted.coordinator)
                    )
                )
            }
            if (resourceNotStarted.isRunning) {
                startNextResource()
            }
        }
    }

    private fun handleStartEvent() {
        prepareResources()

        startNextResource()
    }

    @Suppress("TooGenericExceptionCaught")
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
        coordinator.updateStatus(LifecycleStatus.DOWN)
        createdResources.clear()
        logger.info("Stopped ${coordinator.name}")
        waitForStateChange.forEach {
            it.complete(LifecycleStatus.DOWN)
        }
    }

    private fun handleChildStatusChange(newStatus: LifecycleStatus) {
        if (newStatus != LifecycleStatus.UP) {
            stop()
        } else {
            startNextResource()
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
