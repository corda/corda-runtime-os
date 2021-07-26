package net.corda.lifecycle

/**
 * Purely internal events for the lifecycle coordinator to process. These are used to ensure that state changes to the
 * coordinator always happen on an executor thread. As there should only ever be a single thread processing events at
 * any one time, this ensures there is no race condition in updating coordinator state.
 */

internal typealias TimerEventGenerator = (String) -> TimerEvent

/**
 * Create a new timer.
 */
internal data class SetUpTimer(
    val key: String,
    val delay: Long,
    val timerEventGenerator: TimerEventGenerator
) : LifeCycleEvent

/**
 * Cancel a timer.
 */
internal data class CancelTimer(
    val key: String
) : LifeCycleEvent
