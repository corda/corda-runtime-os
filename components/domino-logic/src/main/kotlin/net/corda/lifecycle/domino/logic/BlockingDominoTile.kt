package net.corda.lifecycle.domino.logic

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StopEvent
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * This class encapsulates domino logic for components that have no dependencies, where we want to delay startup until we receive an
 * external signal.
 *
 * @param startTile complete this future to signal that this tile should start. This is a one shot processes i.e. if the Tile starts,
 * then stops and then starts again, there is no way to delay the Tile starting for the second time. If the future completes exceptionally
 * then the tile will error, there is no way to recover from this state.
 */
class BlockingDominoTile(componentName: String,
                         coordinatorFactory: LifecycleCoordinatorFactory,
                         private val startTile: CompletableFuture<Unit>
): DominoTile() {
    companion object {
        private val instancesIndex = ConcurrentHashMap<String, Int>()
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val coordinatorName: LifecycleCoordinatorName by lazy {
        LifecycleCoordinatorName(
            componentName + "Blocking",
            instancesIndex.compute(this::class.java.simpleName) { _, last ->
                if (last == null) {
                    1
                } else {
                    last + 1
                }
            }.toString()
        )
    }

    override val coordinator = coordinatorFactory.createCoordinator(coordinatorName, EventHandler())
    private val internalState = AtomicReference(LifecycleStatus.DOWN)

    override val dependentChildren: Collection<LifecycleCoordinatorName> = emptyList()

    override fun start() {
        coordinator.start()
        startTile.whenComplete { _, exception ->
            if (exception == null) {
                coordinator.postEvent(AsynchronousReady)
            } else {
                coordinator.postEvent(AsynchronousException(exception))
            }
        }
    }

    override val managedChildren: Collection<NamedLifecycle> = emptyList()

    private object AsynchronousReady : LifecycleEvent
    private data class AsynchronousException(val exception: Throwable) : LifecycleEvent

    private inner class EventHandler : LifecycleEventHandler {
        override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
            val currentInternalState = internalState.get()
            when (event) {
                is AsynchronousReady -> {
                    if (currentInternalState == LifecycleStatus.DOWN) {
                        internalState.set(LifecycleStatus.UP)
                        coordinator.updateStatus(LifecycleStatus.UP)
                    }
                }
                is AsynchronousException -> {
                    if (currentInternalState != LifecycleStatus.ERROR) {
                        internalState.set(LifecycleStatus.ERROR)
                        logger.error("Asynchronous error when starting tile $coordinatorName:", event.exception)
                        coordinator.updateStatus(LifecycleStatus.ERROR, event.exception.toString())
                    }
                }
                is StopEvent -> {
                    if (coordinator.status == LifecycleStatus.UP) {
                        internalState.set(LifecycleStatus.DOWN)
                        coordinator.updateStatus(LifecycleStatus.DOWN)
                    }
                }
            }
        }
    }
}
