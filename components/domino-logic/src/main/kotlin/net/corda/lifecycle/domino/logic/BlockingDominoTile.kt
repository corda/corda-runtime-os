package net.corda.lifecycle.domino.logic

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StopEvent
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * This class encapsulates domino logic for components that have no dependencies, where we want to delay startup until we receive an
 * external signal.
 *
 * @param startTile complete this future to signal that this tile should start. This is a one shot processes i.e. if the Tile starts,
 * then stops and then starts again, there is no way to delay the Tile starting for the second time.
 */
class BlockingDominoTile(componentName: String,
                         coordinatorFactory: LifecycleCoordinatorFactory,
                         private val startTile: CompletableFuture<Unit>
): DominoTile {
    companion object {
        private val instancesIndex = ConcurrentHashMap<String, Int>()
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

    private val coordinator = coordinatorFactory.createCoordinator(coordinatorName, EventHandler())

    override val dependentChildren: Collection<LifecycleCoordinatorName> = emptyList()
    override val managedChildren: Collection<DominoTile> = emptyList()

    override val state: LifecycleStatus
        get() = coordinator.status

    override val isRunning: Boolean
        get() = state == LifecycleStatus.UP

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

    override fun stop() {
        coordinator.postEvent(StopEvent())
    }

    private object AsynchronousReady : LifecycleEvent
    private data class AsynchronousException(val exception: Throwable) : LifecycleEvent

    private inner class EventHandler : LifecycleEventHandler {
        override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
            when (event) {
                is AsynchronousReady -> {
                    if (state != LifecycleStatus.UP) coordinator.updateStatus(LifecycleStatus.UP)
                }
                is AsynchronousException -> {
                    if (state != LifecycleStatus.ERROR) coordinator.updateStatus(LifecycleStatus.ERROR, event.exception.toString())
                }
                is StopEvent -> {
                    if (state == LifecycleStatus.UP) coordinator.updateStatus(LifecycleStatus.DOWN)
                }
            }
        }
    }
}