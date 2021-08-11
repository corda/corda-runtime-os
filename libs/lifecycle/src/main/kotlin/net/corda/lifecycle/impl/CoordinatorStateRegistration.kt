package net.corda.lifecycle.impl

import net.corda.lifecycle.ActiveStateChangeEvent
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleState

internal class CoordinatorStateRegistration(
    val coordinators: List<LifecycleCoordinator>,
    private val localCoordinator: LifecycleCoordinator
) : AutoCloseable {

    private val coordinatorStateMap = coordinators.associateWith { it.activeState }.toMutableMap()

    private var overallState = LifecycleState.DOWN
        set(value) {
            if (field != value) {
                field = value
                localCoordinator.postEvent(ActiveStateChangeEvent(coordinators, value))
            }
        }

    fun setupRegistration() {
        coordinators.forEach {
            it.postEvent(NewDependentCoordinator(localCoordinator))
        }
        calculateOverallState()
    }

    fun updateCoordinatorState(coordinator: LifecycleCoordinator, newState: LifecycleState) {
        coordinatorStateMap.computeIfPresent(coordinator) { _, _ -> newState }
        calculateOverallState()
    }

    private fun calculateOverallState() {
        overallState = if (coordinatorStateMap.values.any { it == LifecycleState.DOWN }) {
            LifecycleState.DOWN
        } else {
            LifecycleState.UP
        }
    }

    override fun close() {
        localCoordinator.postEvent(StopFollowing(this))
    }
}