package net.corda.lifecycle

import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean


/**
 * The class coordinates [LifeCycleEvent] submitted with [postEvent] and [TimerEvent] timers set with [setTimer].
 *
 * @param batchSize max number of events processed in a single [processEvents] call.
 * @param timeout in milliseconds this coordinator stops before to log a warning.
 * @param lifeCycleProcessor method receiving the [LifeCycleEvent] notifications coordinated by this object.
 */
class SimpleLifeCycleCoordinator(
    private val batchSize: Int,
    private val timeout: Long,
    override val lifeCycleProcessor: LifecycleEventHandler,
) : LifeCycleCoordinator {

    companion object {
        private val logger: Logger = contextLogger()
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
     * The executor on which events are processed. Note that all events should be processed in the executor thread, but
     * may be posted from any other thread.
     */
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        val thread = Thread(runnable)
        thread.isDaemon = true
        thread
    }

    /**
     * Process the events in [eventQueue].
     *
     * To improve performance, events are buffered in an array list of [batchSize] length
     * to be accessed by the [lifeCycleProcessor].
     *
     * Exceptions thrown by the [lifeCycleProcessor] are wrapped in an [ErrorEvent] instance,
     * notified immediately to the [lifeCycleProcessor].
     *
     * If [lifeCycleProcessor] thrown an exception handing an [ErrorEvent], this coordinator stops.
     *
     * **NOTE!**
     * **Exception thrown in the processor always stop the coordinator.**
     * **The processor can handle error events and re-post them to the coordinator**
     * **but if they the processor throw an exception, the coordinator stops.**
     *
     * @throws RejectedExecutionException if [eventQueue] is not empty and next execution of this method can't
     *      be scheduled by [scheduleIfRequired].
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
     * Call [processEvents] if not processing and if this coordinator is running, else do nothing.
     *
     * @throws RejectedExecutionException if [processEvents] can't be scheduled for execution by [executorService].
     */
    @Throws(
        RejectedExecutionException::class
    )
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
     *
     * @see [setTimer]
     *
     */
    override fun cancelTimer(key: String) {
        postEvent(CancelTimer(key))
    }

    /**
     * Post the [lifeCycleEvent] to be processed as soon is possible by [lifeCycleProcessor].
     *
     * Events are processed in the order they are posted.
     *
     * **Note! Events posted between last [stop] and next [start] are ignored: this method lags a warning message.**
     *
     * @param lifeCycleEvent to be processed.
     *
     * @throws RejectedExecutionException if [processEvents] can't be scheduled for execution by [executorService].
     */
    @Throws(
        RejectedExecutionException::class
    )
    override fun postEvent(lifeCycleEvent: LifeCycleEvent) {
        lifecycleState.postEvent(lifeCycleEvent)
        scheduleIfRequired()
    }

    /**
     * Schedule the [onTime] event to be processed after [delay] ms.
     *
     * **NOTE! Pending [TimerEvent] are cancelled when [stop] is called.
     *
     * **Note! Timers set between last [stop] and next [start] are ignored: this method logs a warning message.**
     *
     * @param key unique [TimerEvent] identifier.
     * @param delay in milliseconds, when [onTime] is processed.
     * @param onTime scheduled [TimerEvent].
     *
     * @see [cancelTimer]
     *
     * @throws RejectedExecutionException if [executorService] can't schedule [onTime].
     */
    @Throws(
        RejectedExecutionException::class
    )
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
     *
     * This should never be called from the event processor function. Doing so could result in a deadlock.
     *
     * @throws RejectedExecutionException if [executorService] can't schedule [processEvents].
     */
    @Throws(
        RejectedExecutionException::class
    )
    override fun start() {
        postEvent(StartEvent())
    }

    /**
     * Stop this [LifeCycleCoordinator] processing remaining [LifeCycleEvent] in [eventQueue] but
     * cancelling all pending [TimerEvent].
     *
     * Before to stop, this method submits [StopEvent] assuring it is processed.
     *
     * The queue of events submitted with [postEvent] is cleared.
     *
     * If this method spends more than [timeout] milliseconds to stop, it logs a warning message.
     *
     * **NOTE! Events posted after stop and before [start] are ignored.**
     *
     */
    override fun stop() {
        postEvent(StopEvent())
    }
}