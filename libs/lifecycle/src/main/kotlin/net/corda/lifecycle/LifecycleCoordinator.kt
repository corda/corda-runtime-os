package net.corda.lifecycle

/**
 * Interface for coordination of lifecycle events for a component.
 *
 * The coordinator interface is used by components to signal to the lifecycle infrastructure events that affect the
 * component lifecycle. Behind the scenes, a coordinator is responsible for ensuring that these events are delivered to
 * some event handler.
 *
 * The coordinator guarantees that posted events are processed in the order they are processed, and that events will not
 * be processed concurrently.
 */
interface LifecycleCoordinator : Lifecycle {

    /**
     * Submit an event to be processed.
     *
     * Events are guaranteed to be delivered to the user code in the order they are received by the lifecycle library.
     * It is the user's responsibility to ensure that events are posted in the required order, which might matter in
     * multithreading scenarios.
     *
     * Events that are scheduled to be processed when the library is not running will not be delivered to the user event
     * handler. This decision is made at processing time, which ensures that the user event handler will not see any
     * events between a stop and a start event.
     *
     * @param event The event to post
     */
    fun postEvent(event: LifecycleEvent)

    /**
     * Submit an event to be asynchronously processed.
     *
     * If a timer is set for a key that has previously been set, the previous timer is cancelled.
     *
     * The timer functionality is not suitable for very precise timing requirements. The lifecycle library guarantees
     * the timer event will not be delivered before the delay has expired, and that the event will be delivered if the
     * timer is not cancelled. It does not guarantee that the event will be delivered immediately when the timer fires.
     *
     * Timers that fire when the coordinator is not running will not deliver the timer event to the user event handler.
     *
     * @param key A key to identify this timer
     * @param delay The length of time in milliseconds before this timer will fire
     * @param onTime A function to generate the timer event given the key of the timer.
     */
    fun setTimer(key: String, delay: Long, onTime: (String) -> TimerEvent)

    /**
     * Cancel a timer.
     *
     * Provided the cancel request happens before the timer event fires, this guarantees that the timer event will not
     * be delivered to the user event handler.
     *
     * @param key The key of the timer to cancel.
     */
    fun cancelTimer(key: String)

    /**
     * The current state of this lifecycle coordinator.
     *
     * Components should use this to signal when they go up or down. This can be used by dependent components to trigger
     * them to go up or down in turn.
     */
    var activeState: LifecycleState

    // Multiple calls give multiple lists being followed.
    fun follow(coordinators: List<String>) : AutoCloseable
}