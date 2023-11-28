package net.corda.lifecycle

/**
 * The user event handler for the lifecycle library.
 *
 * Consumers of this library should provide an implementation of this interface to define how they handle lifecycle
 * events. At a minimum, the events defined by the coordinator library itself should be handled - the start and stop
 * events.
 *
 * Events are processed on a thread pool. This means that the `processEvent` function is guaranteed to not be called on
 * the same thread that starts the coordinator. Users should take care to synchronize any state that is required in the
 * event handler and externally.
 *
 * Errors thrown in the event handler are caught and redelivered as an ErrorEvent. This gives implementations of this
 * interface a chance to handle any errors in processing. This must be done explicitly by setting the `isHandled` flag
 * on the event. Failure to do this will result in the coordinator stopping (and a stop event being delivered). Note
 * that all errors in the handler are caught within the lifecycle library.
 */
fun interface LifecycleEventHandler {

    /**
     * Process a single event.
     *
     * @param event The event to process
     * @param coordinator A handle to the coordinator. This can be used to post follow up events or set/cancel timers.
     */
    fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator)
}
