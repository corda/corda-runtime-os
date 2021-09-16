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
import java.util.concurrent.atomic.AtomicReference

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
    private val toStart = ConcurrentLinkedDeque<Lifecycle>()

    private val readyFuture = AtomicReference<CompletableFuture<Unit>>(null)

    fun waitForReady() {
        val future = readyFuture.get()
            ?: throw DominoException("Can not get ready future before starting the tile")
        future.join()
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
        readyFuture.updateAndGet {
            it ?: CompletableFuture()
        }
        coordinator.start()
    }

    override fun stop() {
        logger.info("Stopping ${coordinator.name}")
        readyFuture.set(null)
        coordinator.stop()
    }

    fun gotError(error: Throwable) {
        coordinator.postEvent(ErrorEvent(error))
        readyFuture.get()?.completeExceptionally(error)
    }

    private fun handleErrorEvent(event: ErrorEvent) {
        logger.warn("Got error for ${coordinator.name}", event.cause)
        coordinator.updateStatus(LifecycleStatus.ERROR)
        stop()
    }

    private fun startNextResource() {
        val resource = toStart.pollFirst()
        if (resource == null) {
            coordinator.updateStatus(LifecycleStatus.UP)
            readyFuture.get()?.complete(Unit)
        } else {
            resource.start()
            if (resource is DominoTile) {
                keepResources(
                    coordinator.followStatusChanges(
                        setOf(resource.coordinator)
                    )
                )
            }
            if (resource.isRunning) {
                startNextResource()
            }
        }
    }

    private fun handleStartEvent() {
        prepareResources()

        toStart.clear()

        createdResources
            .filterIsInstance<Lifecycle>()
            .forEach {
                toStart.addFirst(it)
            }

        startNextResource()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun handleStopEvent() {
        toStart.clear()
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
