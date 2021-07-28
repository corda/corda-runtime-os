package net.corda.lifecycle

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
) : LifeCycleEvent

/**
 * Cancel a timer.
 */
internal data class CancelTimer(
    val key: String
) : LifeCycleEvent
