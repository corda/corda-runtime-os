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
 * An event representing any error that occurred during the processing of another lifecycle event.
 *
 * The user event handler will be delivered this event immediately on encountering an error, to give the user a chance
 * to handle the error. Whether it has been handled must be explicitly marked by setting the [isHandled] flag.
 *
 * Errors that occur while processing this event are not redelivered. Instead the coordinator stops after processing any
 * outstanding events.
 *
 * @param cause The exception that triggered this error event to be delivered.
 * @param isHandled flag if the error event is handled by the processor.
 *  If [isHandled] is `false` on return from the processor, this will trigger the coordinator to stop.
 */
class ErrorEvent internal constructor(val cause: Throwable, var isHandled: Boolean = false) : LifeCycleEvent

/**
 * The event delivered on the coordinator starting up.
 *
 * The user event handler is guaranteed to see this event first on start up of the coordinator.
 */
class StartEvent internal constructor() : LifeCycleEvent

/**
 * The event delivered on the coordinator shutting down.
 *
 * The user event handler is guaranteed to see this event last on shut down of the coordinator.
 *
 * Note that on delivery of this event, the coordinator will be marked as not running.
 */
class StopEvent internal constructor() : LifeCycleEvent

/**
 * An event delivered after a scheduled timer has fired.
 *
 * Implement this to define an event tied to a timer. The key should match the key that the timer was scheduled under
 * when calling [LifeCycleCoordinator.setTimer].
 */
interface TimerEvent : LifeCycleEvent {

    /**
     * The key for the timer that fired this event.
     */
    val key: String

}
