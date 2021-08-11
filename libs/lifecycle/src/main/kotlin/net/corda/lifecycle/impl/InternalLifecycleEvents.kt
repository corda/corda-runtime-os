package net.corda.lifecycle.impl

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleState
import net.corda.lifecycle.TimerEvent

/**
 * Purely internal events for the lifecycle coordinator to process. These are used to ensure that state changes to the
 * coordinator always happen on an executor thread. As there should only ever be a single thread processing events at
 * any one time, this ensures there is no race condition in updating coordinator state.
 */

/**
 * Create a new timer.
 *
 * This takes the parameters of the `createTimer` function on the API and packages them for processing elsewhere.
 */
internal data class SetUpTimer(
    val key: String,
    val delay: Long,
    val timerEventGenerator: (String) -> TimerEvent
) : LifecycleEvent

/**
 * Cancel a timer.
 */
internal data class CancelTimer(
    val key: String
) : LifecycleEvent

internal data class StartFollowing(val registration: CoordinatorStateRegistration) : LifecycleEvent

internal data class StopFollowing(val registration: CoordinatorStateRegistration) : LifecycleEvent

internal data class NewDependentCoordinator(val coordinator: LifecycleCoordinator) : LifecycleEvent

internal data class ActiveChangeInternal(
    val component: LifecycleCoordinator,
    val newState: LifecycleState
) : LifecycleEvent

/**
 * Indicates that the component has changed state, so this component can inform dependent components of the change.
 *
 * @param newState The new state this component has taken.
 */
internal data class CoordinatorStateChange(val newState: LifecycleState) : LifecycleEvent
