package net.corda.lifecycle.impl

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent

interface LifecycleCoordinatorInternal: LifecycleCoordinator {
    /**
     * Submit an event internally to be processed.  This version is similar to [postEvent]
     * except that messages are *guaranteed* to be processed, even if the coordinator is stopped or closed.
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
    fun postInternalEvent(event: LifecycleEvent)
}
