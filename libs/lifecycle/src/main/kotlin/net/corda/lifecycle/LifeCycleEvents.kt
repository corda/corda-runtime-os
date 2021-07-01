package net.corda.lifecycle

/**
 * Define an event submitted by a [LifeCycleCoordinator]:
 * events are processed by the [LifeCycleCoordinator]'s processor.
 *
 * See the [SimpleLifeCycleCoordinator.lifeCycleProcessor] as example of coordinator's processor.
 *
 * @see [LifeCycleCoordinator.postEvent]
 */
interface LifeCycleEvent

/**
 * Define an error event wrapping the [cause] submitted by a [LifeCycleCoordinator]
 *
 * @param cause caused the error.
 * @param isHandled flag if the error event is handled by the ??? (coordinator or processor?) TO BE DEFINED.
 */
class ErrorEvent(val cause: Throwable, var isHandled: Boolean = false) : LifeCycleEvent

/**
 * Define an event submitted by a [LifeCycleCoordinator] when it starts:
 *
 * @see [LifeCycleCoordinator.start]
 */
class StartEvent internal constructor(): LifeCycleEvent

/**
 * Define an event submitted by a [LifeCycleCoordinator] when it stops.
 *
 * @see [LifeCycleCoordinator.stop]
 */
class StopEvent internal constructor(): LifeCycleEvent

/**
 * Define a scheduled event submitted by a [LifeCycleCoordinator] when the event happens.
 *
 * @see [LifeCycleCoordinator.setTimer]
 */
interface TimerEvent : LifeCycleEvent {

    /**
     * Unique identifier for the scheduled event.
     *
     * @see [LifeCycleCoordinator.setTimer]
     * @see [LifeCycleCoordinator.cancelTimer]
     */
    val key: String

}
