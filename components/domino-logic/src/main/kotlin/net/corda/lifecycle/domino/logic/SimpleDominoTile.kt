package net.corda.lifecycle.domino.logic

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.domino.logic.DominoTileState.Created
import net.corda.lifecycle.domino.logic.DominoTileState.Started
import net.corda.lifecycle.domino.logic.DominoTileState.StoppedByParent
import net.corda.lifecycle.domino.logic.DominoTileState.StoppedDueToBadConfig
import net.corda.lifecycle.domino.logic.DominoTileState.StoppedDueToChildStopped
import net.corda.lifecycle.domino.logic.DominoTileState.StoppedDueToError
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * This class encapsulates a very simple domino logic for components that have no dependencies and
 * have external control over the tile state.
 *
 * After a [start] was called, calling the [updateState] will update the state of the tile and report
 * to any parent component.
 *
 * @param componentName The name of the component
 * @param coordinatorFactory A coordinator factory.
 */
class SimpleDominoTile(
    componentName: String,
    coordinatorFactory: LifecycleCoordinatorFactory,
) : DominoTile {
    companion object {
        private val instancesIndex = ConcurrentHashMap<String, Int>()
    }
    override val coordinatorName: LifecycleCoordinatorName by lazy {
        LifecycleCoordinatorName(
            componentName,
            instancesIndex.compute(this::class.java.simpleName) { _, last ->
                if (last == null) {
                    1
                } else {
                    last + 1
                }
            }.toString()
        )
    }
    private val logger by lazy {
        LoggerFactory.getLogger(coordinatorName.toString())
    }
    private val coordinator = coordinatorFactory.createCoordinator(coordinatorName, EventHandler())

    private val currentState = AtomicReference(Created)

    override val state: DominoTileState
        get() = currentState.get()

    override val isRunning: Boolean
        get() = state == Started

    override val dependentChildren: Collection<DominoTile> = emptyList()
    override val managedChildren: Collection<DominoTile> = emptyList()

    fun updateState(newState: DominoTileState) {
        val oldState = currentState.getAndSet(newState)
        if (newState != oldState) {
            val status = when (newState) {
                Started -> LifecycleStatus.UP
                StoppedDueToBadConfig, StoppedByParent, StoppedDueToChildStopped -> LifecycleStatus.DOWN
                StoppedDueToError -> LifecycleStatus.ERROR
                Created -> null
            }
            status?.let { coordinator.updateStatus(it) }
            coordinator.postCustomEventToFollowers(StatusChangeEvent(newState))
            logger.info("State updated from $oldState to $newState")
        }
    }

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        updateState(StoppedByParent)
    }

    override fun close() {
        coordinator.close()
    }
    private class EventHandler : LifecycleEventHandler {
        override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
            // Nothing to do
        }
    }
}
