package net.corda.lifecycle

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
 * @param batchSize max number of events processed in a single [processEvents] call.
 * @param timeout in milliseconds this coordinator stops before to log a warning.
 * @param lifeCycleProcessor The user event handler for lifecycle events.
 */
class SimpleLifecycleCoordinator(
    private val batchSize: Int,
    private val timeout: Long,
    override val lifeCycleProcessor: LifecycleEventHandler,
) : LifecycleCoordinator {

    companion object {
        private val logger: Logger = contextLogger()

        /**
         * The minimum number of threads to keep active in the threadpool.
         *
         * Under load, the number of threads may increase. By keeping a minimum of one, the lifecycle library should
         * remain responsive to change while not consuming excessive resources.
         */
        private const val MIN_THREADS = 100

        /**
         * The executor on which events are processed. Note that all events should be processed on an executor thread,
         * but they may be posted from any thread. Different events may be processed on different executor threads.
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
    private val processor = LifecycleProcessor(lifecycleState, lifeCycleProcessor)

    /**
     * `true` if [processEvents] is executing. This is used to ensure only one attempt at processing the event queue is
     * scheduled at a time.
     */
    private val isScheduled = AtomicBoolean(false)

    /**
     * Process the events in [eventQueue].
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
            logger.warn("Unhandled error event! Life-Cycle coordinator stops.")
            stop()
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
        if (!isScheduled.getAndSet(true)) {
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
     * Cancel the [TimerEvent] uniquely identified by [key].
     * If [key] doesn't identify any [TimerEvent] scheduled with [setTimer], this method doesn't anything.
     *
     * @param key identifying the [TimerEvent] to cancel,
     */
    override fun cancelTimer(key: String) {
        postEvent(CancelTimer(key))
    }

    /**
     * Post the [event] to be processed as soon is possible by [lifeCycleProcessor].
     *
     * Events are processed in the order they are posted.
     *
     * @param event to be processed.
     */
    override fun postEvent(event: LifecycleEvent) {
        lifecycleState.postEvent(event)
        scheduleIfRequired()
    }

    /**
     * Schedule a timer event to be posted to the event queue after some delay.
     *
     * @param key unique [TimerEvent] identifier.
     * @param delay in milliseconds, when [onTime] is processed.
     * @param onTime Function generating the timer event to post to the queue. The input parameter is the key.
     */
    override fun setTimer(key: String, delay: Long, onTime: (String) -> TimerEvent) {
        postEvent(SetUpTimer(key, delay, onTime))
    }

    /**
     * Return `true` in this coordinator is processing posted events.
     */
    override val isRunning: Boolean
        get() = lifecycleState.isRunning

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
        postEvent(StopEvent())
    }
}