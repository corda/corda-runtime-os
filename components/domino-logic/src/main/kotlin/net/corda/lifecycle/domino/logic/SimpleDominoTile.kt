package net.corda.lifecycle.domino.logic

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StopEvent
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
) : DominoTile() {
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
    override val coordinator = coordinatorFactory.createCoordinator(coordinatorName, EventHandler())

    private val currentState = AtomicReference(LifecycleStatus.DOWN)

    override val dependentChildren: Collection<LifecycleCoordinatorName> = emptyList()
    override val managedChildren: Collection<LifecycleWithCoordinatorName> = emptyList()

    fun updateState(newState: LifecycleStatus) {
        val oldState = currentState.getAndSet(newState)
        if (newState != oldState) {
            coordinator.updateStatus(newState)
            logger.info("State updated from $oldState to $newState")
        }
    }

    private inner class EventHandler : LifecycleEventHandler {
        override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
            when (event) {
                is StopEvent -> {
                    updateState(LifecycleStatus.DOWN)
                }
            }
        }
    }
}
