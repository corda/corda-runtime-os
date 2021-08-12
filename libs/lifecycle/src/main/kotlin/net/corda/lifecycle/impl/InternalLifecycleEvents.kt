package net.corda.lifecycle.impl

import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
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

internal data class NewRegistration(val registration: Registration) : LifecycleEvent

internal data class CancelRegistration(val registration: Registration) : LifecycleEvent

/**
 * Indicates that the component has changed state, so this component can inform dependent components of the change.
 *
 * @param newStatus The new state this component has taken.
 */
internal data class StatusChange(val newStatus: LifecycleStatus) : LifecycleEvent
