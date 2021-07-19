package net.corda.lifecycle

import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


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
    override val lifeCycleProcessor: (LifeCycleEvent: LifeCycleEvent, lifecycleCoordinator: LifeCycleCoordinator) -> Unit,
) : LifeCycleCoordinator {

    companion object {

        private val logger: Logger = contextLogger()

    }

    /**
     * Synchronize startup and shutdown procedures, to ensure there is no overlap of start and stop methods.
     */
    private val lock = ReentrantLock()

    /**
     * Used to ensure that startup is blocked on the cleanup from a previous shutdown is completed.
     *
     * This is required as the cleanup must occur on the executor thread, to ensure that events are always processed on
     * the same thread. However, stop may be called from threads that are not the executor thread, so the lock is not
     * sufficient.
     */
    private val cleanupCondition = lock.newCondition()

    /**
     * Set to the value of the [Thread.getId] of the thread running [processEvents], or `null` if [processEvents] is not
     * running.
     *
     * This is primarily used to ensure that no attempt is made to schedule something on the executor thread.
     */
    private var executorThreadID: Long? = null

    /**
     * The event queue and timer state for this lifecycle processor.
     */
    private val eventQueueManager = LifecycleEventQueueManager(batchSize) { lifecycleEvent ->
        lifeCycleProcessor(lifecycleEvent, this)
    }

    /**
     * `true` if [processEvents] is executing. This is used to ensure only one attempt at processing the event queue is
     * scheduled at a time.
     */
    private val isScheduled = AtomicBoolean(false)

    /**
     * Indicates whether cleanup from when the coordinator was previously started is still in progress.
     *
     * This is used to block restarting the coordinator until the previous run has completed.
     */
    private val cleanupInProgress = AtomicBoolean(false)

    /**
     * Backing variable for the public [isRunning], but atomically updated.
     */
    private val _isRunning = AtomicBoolean(false)

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
        executorThreadID = Thread.currentThread().id
        val shutdown = !eventQueueManager.processEvents()
        isScheduled.set(false)
        if (shutdown) {
            logger.warn("Unhandled error event! Life-Cycle coordinator stops.")
            stop()
        } else {
            executorThreadID = null
            if (!eventQueueManager.isEmpty) {
                scheduleIfRequired()
            }
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
     * Cancel the [TimerEvent] uniquely identified by [key].
     * If [key] doesn't identify any [TimerEvent] scheduled with [setTimer], this method doesn't anything.
     *
     * @param key identifying the [TimerEvent] to cancel,
     *
     * @see [setTimer]
     *
     */
    override fun cancelTimer(key: String) {
        eventQueueManager.cancelTimer(key)
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
        if (isRunning) {
            eventQueueManager.postEvent(lifeCycleEvent)
            scheduleIfRequired()
        } else {
            logger.warn("Cannot post event $lifeCycleEvent as the lifecycle coordinator is not currently running")
        }
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
        if (isRunning) {
            eventQueueManager.setTimer(
                key,
                executor.schedule({ postEvent(onTime(key)) }, delay, TimeUnit.MILLISECONDS)
            )
        } else {
            logger.warn("Life-Cycle coordinator not running: timer set with key = $key is ignored!")
        }
    }

    /**
     * Return `true` in this coordinator is processing posted events.
     */
    override val isRunning: Boolean
        get() = _isRunning.get()

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
        if (!isRunning) {
            lock.withLock {
                // Must wait here for previous cleanup to prevent a race condition where the start event posted below is
                // deleted by a previous cleanup run.
                while (cleanupInProgress.get()) {
                    cleanupCondition.await()
                }
                eventQueueManager.postEvent(StartEvent())
                scheduleIfRequired()
                _isRunning.set(true)
            }
        }
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
        if (isRunning) {
            lock.withLock {
                cleanupInProgress.set(true)
                eventQueueManager.postEvent(StopEvent())
                _isRunning.set(false)
            }
            // Because cleanup processes outstanding events, it must be run on the executor thread. This also means that
            // the lock does not protect against running start before cleanup has completed, so a condition is used
            // instead to ensure start does not run until cleanup has finished.
            if (Thread.currentThread().id != executorThreadID) {
                executor.submit {
                    cleanup()
                }
            } else {
                cleanup()
            }
        }
    }

    private fun cleanup() {
        lock.withLock {
            eventQueueManager.cleanup()
            cleanupInProgress.set(false)
            cleanupCondition.signal()
        }
    }

}