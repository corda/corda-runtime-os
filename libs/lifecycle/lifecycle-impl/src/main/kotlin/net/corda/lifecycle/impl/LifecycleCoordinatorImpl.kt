package net.corda.lifecycle.impl

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.TimerEvent
import net.corda.lifecycle.impl.registry.LifecycleRegistryCoordinatorAccess
import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger
import java.util.concurrent.Executors
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
 * @param registry The registry this coordinator has been registered with. Used to update status for monitoring purposes
 * @param lifeCycleProcessor The user event handler for lifecycle events.
 */
class LifecycleCoordinatorImpl(
    override val name: String,
    batchSize: Int,
    registry: LifecycleRegistryCoordinatorAccess,
    lifeCycleProcessor: LifecycleEventHandler,
) : LifecycleCoordinator {

    companion object {
        private val logger: Logger = contextLogger()

        /**
         * The minimum number of threads to keep active in the threadpool.
         *
         * Under load, the number of threads may increase. By keeping a minimum of one, the lifecycle library should
         * remain responsive to change while not consuming excessive resources.
         */
        private const val MIN_THREADS = 1

        /**
         * The executor on which events are processed. Note that all events should be processed on an executor thread,
         * but they may be posted from any thread. Different events may be processed on different executor threads.
         *
         * The coordinator guarantees that the event processing task is only scheduled once. This means that event
         * processing is effectively single threaded in the sense that no event processing will happen concurrently.
         *
         * By sharing a threadpool among coordinators, it should be possible to reduce resource usage when in a stable
         * state.
         */
        private val executor = Executors.newScheduledThreadPool(MIN_THREADS) { runnable ->
            val thread = Thread(runnable)
            thread.isDaemon = true
            thread
        }
    }

    /**
     * The event queue and timer state for this lifecycle processor.
     */
    private val lifecycleState = LifecycleStateManager(batchSize)

    /**
     * The processor for this coordinator.
     */
    private val processor = LifecycleProcessor(name, lifecycleState, registry, lifeCycleProcessor)

    /**
     * `true` if [processEvents] is executing. This is used to ensure only one attempt at processing the event queue is
     * scheduled at a time.
     */
    private val isScheduled = AtomicBoolean(false)

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
        } else {
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
        // Must be this way around as isScheduled should not be set if no task is scheduled.
        if (lifecycleState.eventsQueued() && !isScheduled.getAndSet(true)) {
            executor.submit(::processEvents)
        }
    }

    /**
     * Schedule a new timer event to be delivered.
     *
     * This is invoked from the processor when processing a new timer set up event.
     */
    private fun createTimer(timerEvent: TimerEvent, delay: Long): ScheduledFuture<*> {
        return executor.schedule({ postEvent(timerEvent) }, delay, TimeUnit.MILLISECONDS)
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
        lifecycleState.postEvent(event)
        scheduleIfRequired()
    }

    /**
     * See [LifecycleCoordinator].
     */
    override fun setTimer(key: String, delay: Long, onTime: (String) -> TimerEvent) {
        postEvent(SetUpTimer(key, delay, onTime))
    }

    /**
     * See [LifecycleCoordinator].
     */
    override fun cancelTimer(key: String) {
        postEvent(CancelTimer(key))
    }

    /**
     * See [LifecycleCoordinator].
     */
    override fun updateStatus(newStatus: LifecycleStatus, reason: String) {
        postEvent(StatusChange(newStatus, reason))
    }

    /**
     * See [LifecycleCoordinator].
     */
    override fun followStatusChanges(coordinators: Set<LifecycleCoordinator>): RegistrationHandle {
        val registration = Registration(coordinators, this)
        postEvent(TrackRegistration(registration))
        coordinators.forEach { it.postEvent(NewRegistration(registration)) }
        return registration
    }

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
     * Start this coordinator.
     */
    override fun start() {
        postEvent(StartEvent())
    }

    /**
     * Stop this coordinator.
     *
     * Note that this does not immediately stop, instead a graceful shutdown is attempted where any outstanding events
     * are delivered to the user code.
     */
    override fun stop() {
        stopInternal(false)
    }
}