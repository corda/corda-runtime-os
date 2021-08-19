package net.corda.lifecycle.registry

/**
 * Functional interface defining a status change event handler.
 *
 * An event handler should be registered with the lifecycle registry. The event handler will be called once per
 * coordinator status change. This can be used by monitoring components to update any metrics or displays they may be
 * servicing.
 */
fun interface StatusChangeEventHandler {

    /**
     * Handle a coordinator status change.
     *
     * The registry provides both the delta from the previous state and the full current state of coordinator statuses,
     * to allow the client to decide what information it is interested in. The handler will be invoked both when
     * existing coordinators undergo a status change and when a new coordinator is first registered.
     *
     * The handler is not guaranteed to be invoked on the same thread each time, but is guaranteed to not be invoked
     * concurrently.
     *
     * @param statuses The full set of current coordinator statuses
     * @param statusChange The coordinator that has changed status
     */
    fun onStatusChange(statuses: Map<String, CoordinatorStatus>, statusChange: CoordinatorStatus)
}