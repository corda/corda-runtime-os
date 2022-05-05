package net.corda.lifecycle.domino.logic.util

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.DominoTileState
import net.corda.lifecycle.domino.logic.StatusChangeEvent
import java.util.concurrent.ConcurrentHashMap

class PartitionObserverDominoTile(componentName: String, coordinatorFactory: LifecycleCoordinatorFactory): DominoTile {

    companion object {
        private val instancesIndex = ConcurrentHashMap<String, Int>()
    }

    private object StartTile : LifecycleEvent
    private object StopTile : LifecycleEvent

    @Volatile
    override var state: DominoTileState = DominoTileState.Created
    override val dependentChildren: Collection<DominoTile> = emptySet()
    override val managedChildren: Collection<DominoTile> = emptySet()
    override val isRunning: Boolean
        get() = state == DominoTileState.Started

    private var partitionsAssigned = false

    override val coordinatorName: LifecycleCoordinatorName by lazy {
        LifecycleCoordinatorName(
            componentName,
            instancesIndex.compute(componentName) { _, last ->
                if (last == null) {
                    1
                } else {
                    last + 1
                }
            }.toString()
        )
    }
    private val coordinator = coordinatorFactory.createCoordinator(coordinatorName, EventHandler())

    override fun start() {
        coordinator.start()
        coordinator.postEvent(StartTile)
    }

    override fun stop() {
        coordinator.postEvent(StopTile)
    }

    fun partitionAllocationChanged(allocatedPartitions: List<Int>) {
        if (allocatedPartitions.isEmpty()) {
            partitionsAssigned = false
            val newState = DominoTileState.StoppedDueToError
            state = newState
            coordinator.updateStatus(LifecycleStatus.ERROR)
            coordinator.postCustomEventToFollowers(StatusChangeEvent(newState))
        } else {
            partitionsAssigned = true
            val newState = DominoTileState.Started
            state = newState
            coordinator.updateStatus(LifecycleStatus.UP)
            coordinator.postCustomEventToFollowers(StatusChangeEvent(newState))
        }
    }

    private inner class EventHandler : LifecycleEventHandler {
        override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
            when(event) {
                is StartTile -> {
                    if (partitionsAssigned) {
                        val newState = DominoTileState.Started
                        state = newState
                        coordinator.updateStatus(LifecycleStatus.UP)
                        coordinator.postCustomEventToFollowers(StatusChangeEvent(newState))
                    }
                }
                is StopTile -> {
                    // not reacting here, as there is nothing to stop.
                    // Partitions will be naturally re-allocated when the corresponding subscription stops
                }
            }
        }

    }

}