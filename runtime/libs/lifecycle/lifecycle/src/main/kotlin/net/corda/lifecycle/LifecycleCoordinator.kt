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
interface LifecycleCoordinator : Lifecycle, AutoCloseable {

    /**
     * The name of this coordinator.
     *
     * Primarily useful for diagnostic purposes. This is the same name as that provided to the factory at coordinator
     * construction, so it should mirror the component name.
     */
    val name: LifecycleCoordinatorName

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
     * The current status of this lifecycle coordinator.
     *
     * Components should use this to signal when they go up or down. This can be used by dependent components to trigger
     * them to go up or down in turn.
     */
    val status: LifecycleStatus

    /**
     * Update the status of this coordinator.
     *
     * The status of this coordinator is updated in the processing thread, and therefore may not be reflected by the
     * status property straight away. Note that the status may also be changed internally if e.g. an unhandled error is
     * encountered, or the coordinator is stopped.
     *
     * The status will not be updated if this is called while the coordinator is stopped.
     *
     * @param newStatus The new status of this lifecycle coordinator.
     * @param reason A diagnostic string describing why this status has been entered. This will be handed over to the
     *               registry for monitoring purposes.
     */
    fun updateStatus(newStatus: LifecycleStatus, reason: String = "Status has changed to $newStatus")

    /**
     * Posts a custom event to anyone registered on this coordinator.
     * This will emit a [CustomEvent] with the specified payload to all the registered coordinators.
     *
     * @param eventPayload the payload of the custom event.
     */
    fun postCustomEventToFollowers(eventPayload: Any)

    /**
     * Register for status changes from a set of dependent coordinators.
     *
     * On calling this function, this coordinator is registered for status changes from the provided coordinators.
     * This is done in aggregate, so this coordinator only receives an up event for the registration if all the
     * underlying coordinators report themselves as being up. Similarly, if a single underlying coordinator goes down,
     * an event is delivered signaling that the registration as a whole is down. Changes in the registration are
     * delivered as [RegistrationStatusChangeEvent]s.
     *
     * The registration handle can be used to terminate the registration by calling [RegistrationHandle.close], which
     * removes the registration from the underlying coordinators. Note that no event is delivered to the client event
     * handler on unregistration.
     *
     * @param coordinators The set of coordinators to register for status changes on.
     * @return The registration. The same handle is returned on status change events delivered to the client event
     *         handler.
     */
    fun followStatusChanges(coordinators: Set<LifecycleCoordinator>): RegistrationHandle

    /**
     * Register for status changes from a set of dependent coordinators.
     *
     * This version uses the coordinator names registered with the global registry to find the right coordinator to
     * register on. This is useful in cases where a component does not want to declare a compile time dependency on
     * another component purely to obtain the coordinator instance of that component, or where the coordinator instance
     * is not exposed.
     *
     * In cases where a component has access to some coordinators they wish to register on but not others, this API can
     * be used in combination with the `name` field on the coordinator to register on the full set.
     *
     * @param names The names of the coordinators to register on.
     * @return The registration. The same handle is returned on status change events delivered to the client event
     *         handler.
     * @throws LifecycleException if an invalid name was provided in the set of lifecycle coordinators.
     */
    fun followStatusChangesByName(coordinatorNames: Set<LifecycleCoordinatorName>): RegistrationHandle

    /**
     * Create a resource which can be closed and recreated as necessary.  This resource will be managed by
     * the [LifecycleCoordinator].  If you need to access the resource for any reason then use
     * [getManagedResource].
     *
     * Any subsequent calls to [createManagedResource] with the same [name] will result in the previous
     * resource being closed and a new one taking its place.  Therefore, you do not need to close the
     * former resource yourself.
     *
     * @see getManagedResource
     *
     * @param name a unique identifier for the resource
     * @param generator the lambda for creating the resource
     * @return The newly created resource
     */
    fun <T: Resource> createManagedResource(name: String, generator: () -> T): T

    /**
     * Retrieve (by [name]) a managed resource from this coordinator.  The resource will have been
     * created by [createManagedResource]
     *
     * @see createManagedResource
     *
     * @param name the name of the resource.  Must match the name given in [createManagedResource]
     *
     * @return the resource associated by [name] or null if not available
     */
    fun <T: Resource> getManagedResource(name: String) : T?

    /**
     * Closes _only_ the given resources.  If no resources are provided (i.e. [resources] is null)
     * then all managed resources are closed.
     *
     * It is not expected that you will often need to close resources yourself.  When generating a
     * new resource for a given [name] the former resource will be closed for you.
     *
     * Additionally, all managed resources will be closed by the [LifecycleCoordinator] when
     * [LifecycleCoordinator.stop] or [LifecycleCoordinator.close] is called.
     *
     * @param resources the set of resources which should be closed
     */
    fun closeManagedResources(resources: Set<String>? = null)

    /**
     * Flag indicating whether this coordinator has been closed.
     *
     * A closed coordinator is stopped and can no longer be restarted. If a component has its coordinator closed, it
     * should be recreated.
     */
    val isClosed: Boolean

    /**
     * Close this lifecycle coordinator.
     *
     * Closing a lifecycle coordinator triggers a stop event as if the coordinator were stopped normally, but also
     * unregisters it from the coordinator registry and prevents the coordinator from being restarted. This should be
     * called if the component is to be permanently removed so cleanup can occur.
     *
     * Note that after calling close, any call to other APIs will lead to a [LifecycleException] being thrown.
     *
     * @throws LifecycleException if there are any registrations involving this coordinator.
     */
    override fun close()
}
