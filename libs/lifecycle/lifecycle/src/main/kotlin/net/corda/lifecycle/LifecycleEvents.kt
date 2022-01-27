package net.corda.lifecycle

/**
 * A lifecycle event to be processed by a coordinator.
 *
 * This interface should be used by client code to define lifecycle events for the component.
 *
 * @see [LifecycleCoordinator.postEvent]
 */
interface LifecycleEvent

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
data class ErrorEvent(val cause: Throwable, var isHandled: Boolean = false) : LifecycleEvent

/**
 * The event delivered on the coordinator starting up.
 *
 * The user event handler is guaranteed to see this event first on start up of the coordinator.
 */
class StartEvent : LifecycleEvent

/**
 * The event delivered on the coordinator shutting down.
 *
 * The user event handler is guaranteed to see this event last on shut down of the coordinator.
 *
 * Note that on delivery of this event, the coordinator will be marked as not running.
 *
 * @param errored Flag indicating if this stop event happened due to an error occurring. Used internally to set the
 *                coordinator status correctly.
 */
data class StopEvent(val errored: Boolean = false) : LifecycleEvent

/**
 * An event delivered after a scheduled timer has fired.
 *
 * Implement this to define an event tied to a timer. The key should match the key that the timer was scheduled under
 * when calling [LifecycleCoordinator.setTimer].
 */
interface TimerEvent : LifecycleEvent {

    /**
     * The key for the timer that fired this event.
     */
    val key: String
}

/**
 * An event signalling that the status of a registration has changed.
 *
 * This event will only be delivered when all the underlying coordinators of the registration go up, or a single one
 * goes down after all had previously been up.
 *
 * @param registration The registration this event is signaling for
 * @param status The new status of the registration
 */
data class RegistrationStatusChangeEvent(
    val registration: RegistrationHandle,
    val status: LifecycleStatus
) : LifecycleEvent

/**
 * A custom event that can be sent from a coordinator to any coordinators that have followed it.
 *
 * This can be used to transmit custom signals between coordinators.
 * Event handlers can selectively handle incoming custom events based on the type of the [payload] field.
 * In this way, they can handle any events that they are aware of and ignore gracefully any other events they are agnostic of.
 *
 * @param registration the registration the custom event was be sent to.
 * @param payload the payload of the custom event.
 */
class CustomEvent(val registration: RegistrationHandle, val payload: Any): LifecycleEvent