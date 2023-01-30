package net.corda.lifecycle.impl

import net.corda.lifecycle.CustomEvent
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleCoordinatorScheduler
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleException
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.Resource
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.TimerEvent
import net.corda.lifecycle.impl.registry.LifecycleRegistryCoordinatorAccess
import net.corda.lifecycle.registry.LifecycleRegistryException
import net.corda.v5.base.util.trace
import net.corda.v5.base.util.uncheckedCast
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean


/**
 * A manager of lifecycle events for a single component.
 *
 * This class implements the coordinator API, which can be used to model component lifecycle as a queue of events to be
 * handled. The main responsibility of this class is to receive API calls and schedule the processing of lifecycle
 * events.
 *
 * Events are scheduled to be processed on a thread pool. This class ensures that only one call to process is scheduled
 * at once.
 *
 * @param name The name of the component for this lifecycle coordinator.
 * @param batchSize max number of events processed in a single [processEvents] call.
 * @param dependentComponents A set of static singleton component dependencies this coordinator will track.
 *                            These dependencies will be stopped/started alongside this component.  Note that
 *                            the component for this coordinator should also be a static singleton component.
 * @param registry The registry this coordinator has been registered with. Used to update status for monitoring purposes
 * @param lifecycleEventHandler The user event handler for lifecycle events.
 */
@Suppress("TooManyFunctions", "LongParameterList")
class LifecycleCoordinatorImpl(
    override val name: LifecycleCoordinatorName,
    batchSize: Int,
    dependentComponents: DependentComponents?,
    private val registry: LifecycleRegistryCoordinatorAccess,
    private val scheduler: LifecycleCoordinatorScheduler,
    lifecycleEventHandler: LifecycleEventHandler,
) : LifecycleCoordinatorInternal {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    /**
     * The event queue and timer state for this lifecycle processor.
     */
    private val lifecycleState = LifecycleStateManager(batchSize)

    /**
     * The processor for this coordinator.
     */
    private val processor = LifecycleProcessor(name, lifecycleState, registry, dependentComponents, lifecycleEventHandler)

    /**
     * `true` if [processEvents] is executing. This is used to ensure only one attempt at processing the event queue is
     * scheduled at a time.
     */
    private val isScheduled = AtomicBoolean(false)

    /**
     * Backing atomic boolean for the [isClosed] flag. Used to prevent further events being sent when the coordinator is
     * closed.
     *
     * This is unique among state on the coordinator as it is important for the coordinator to be marked as closed
     * immediately to ensure no further attempts are made to create registrations on the coordinator once it has been
     * shut down.
     */
    private val _isClosed = AtomicBoolean(false)

    /**
     * Process a batch of events in the event queue.
     *
     * The main processing functionality is delegated to the LifecycleProcessor class. On a processing error, the
     * coordinator is stopped.
     *
     * @throws RejectedExecutionException if the executor cannot schedule the next process attempt.
     */
    @Throws(
        RejectedExecutionException::class
    )
    private fun processEvents() {
        val shutdown = !processor.processEvents(this, ::createTimer)
        isScheduled.set(false)
        if (shutdown) {
            stopInternal(true)
        } else if (lifecycleState.eventsQueued()) {
            scheduleIfRequired()
        }
    }

    /**
     * Schedule the processing of any lifecycle events.
     *
     * This function ensures that only a single task to process events is scheduled at once. This prevents race
     * conditions is processing events.
     */
    private fun scheduleIfRequired() {
        if (!isScheduled.getAndSet(true)) {
            scheduler.execute(::processEvents)
        }
    }

    /**
     * Schedule a new timer event to be delivered.
     *
     * This is invoked from the processor when processing a new timer set up event.
     */
    private fun createTimer(timerEvent: TimerEvent, delay: Long): ScheduledFuture<*> {
        return scheduler.timerSchedule({ postEvent(timerEvent) }, delay, TimeUnit.MILLISECONDS)
    }

    /**
     * Stop the coordinator. The posted event signals if this stop is due to an error.
     *
     * @param errored True if the coordinator was stopped due to an error.
     */
    private fun stopInternal(errored: Boolean) {
        postEvent(StopEvent(errored))
    }

    /**
     * See [LifecycleCoordinator].
     */
    override fun postEvent(event: LifecycleEvent) {
        if (isClosed) {
            logger.warn("An attempt was made to use coordinator $name after it has been closed. Event: $event")
            throw LifecycleException("No events can be posted to a closed coordinator. Event: $event")
        }
        postInternalEvent(event)
    }

    /**
     * See [LifecycleCoordinatorInternal].
     */
    override fun postInternalEvent(event: LifecycleEvent) {
        lifecycleState.postEvent(event)
        scheduleIfRequired()
    }

    /**
     * See [LifecycleCoordinator].
     */
    override fun setTimer(key: String, delay: Long, onTime: (String) -> TimerEvent) {
        logger.trace { "$name: Creating timer for key $key" }
        postEvent(SetUpTimer(key, delay, onTime))
    }

    /**
     * See [LifecycleCoordinator].
     */
    override fun cancelTimer(key: String) {
        logger.trace { "$name: Cancelling timer for key $key" }
        if (!isClosed) {
            // No need to cancel any timer if the coordinator is closed
            postEvent(CancelTimer(key))
        }
    }

    /**
     * See [LifecycleCoordinator].
     */
    override fun updateStatus(newStatus: LifecycleStatus, reason: String) {
        logger.trace { "$name: Updating status from ${lifecycleState.status} to $newStatus ($reason)" }
        if (!isClosed) {
            postEvent(StatusChange(newStatus, reason))
        }
    }

    /**
     * See [LifecycleCoordinator].
     */
    override fun postCustomEventToFollowers(eventPayload: Any) {
        logger.trace { "$name: Posting custom event with ${eventPayload.javaClass.simpleName} payload to registrations." }
        lifecycleState.registrations.forEach { it.postCustomEvent(CustomEvent(it, eventPayload)) }
    }

    /**
     * See [LifecycleCoordinator].
     */
    override fun followStatusChanges(coordinators: Set<LifecycleCoordinator>): RegistrationHandle {
        logger.trace { "$name: Registering on coordinators: ${coordinators.map { it.name }}" }
        if (coordinators.contains(this)) {
            logger.error("An attempt was made to register coordinator $name on itself")
            throw LifecycleException("Attempt was made to register coordinator $name on itself")
        }
        val coordinatorRegistrationAccess = coordinators.map { it as LifecycleCoordinatorInternal }.toSet()
        val registration = Registration(coordinatorRegistrationAccess, this)
        postEvent(TrackRegistration(registration))
        coordinators.forEach { it.postEvent(NewRegistration(registration)) }
        return registration
    }

    /**
     * See [LifecycleCoordinator].
     */
    override fun followStatusChangesByName(coordinatorNames: Set<LifecycleCoordinatorName>): RegistrationHandle {
        val coordinators = try {
            coordinatorNames.mapTo(LinkedHashSet(), registry::getCoordinator)
        } catch (e: LifecycleRegistryException) {
            logger.error("Failed to register on coordinator as an invalid name was provided. ${e.message}", e)
            throw LifecycleException("Failed to register on a coordinator as an invalid name was provided", e)
        }
        return followStatusChanges(coordinators)
    }

    override fun <T : Resource> createManagedResource(name: String, generator: () -> T) {
        processor.addManagedResource(name, generator)
    }

    override fun <T: Resource> getManagedResource(name: String) : T? {
        return uncheckedCast(processor.getManagedResource(name))
    }

    override fun closeManagedResources(resources: Set<String>?) = processor.closeManagedResources(resources)

    /**
     * See [LifecycleCoordinator].
     */
    override val isRunning: Boolean
        get() = lifecycleState.isRunning

    /**
     * See [LifecycleCoordinator].
     */
    override val status: LifecycleStatus
        get() = lifecycleState.status

    /**
     * See [LifecycleCoordinator].
     */
    override val isClosed: Boolean
        get() = _isClosed.get()

    /**
     * Start this coordinator.
     */
    override fun start() {
        logger.trace { "$name: Starting coordinator" }
        postEvent(StartEvent())
    }

    /**
     * Stop this coordinator.
     *
     * Note that this does not immediately stop, instead a graceful shutdown is attempted where any outstanding events
     * are delivered to the user code.
     */
    override fun stop() {
        logger.trace { "$name: Stopping coordinator" }
        stopInternal(false)
    }

    override fun close() {
        if (_isClosed.compareAndSet(false, true)) {
            logger.trace { "$name: Closing coordinator" }
            postInternalEvent(StopEvent())
            postInternalEvent(CloseCoordinator())
            registry.removeCoordinator(name)
        }
    }
}
