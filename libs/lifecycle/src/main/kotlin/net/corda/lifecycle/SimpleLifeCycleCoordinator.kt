package net.corda.lifecycle

import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
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
    val batchSize: Int,
    val timeout: Long,
    override val lifeCycleProcessor: (LifeCycleEvent: LifeCycleEvent, lifecycleCoordinator: LifeCycleCoordinator) -> Unit,
) : LifeCycleCoordinator {

    companion object {

        private val logger: Logger = contextLogger()

    } //~ companion

    /**
     * Synchronize the access to [executorService].
     */
    private val lock = ReentrantLock()

    /**
     * Set to the value of the [Thread.getId] of the thread running [processEvents],
     * `null` if [processEvents] is not running.
     */
    private var executorThreadID: Long? = null

    /**
     * It owns [processEvents] when this coordinator is running.
     *
     * Must be synchronized with [lock].
     *
     * @see [start]
     * @see [stop]
     */
    @Volatile
    private var executorService: ScheduledExecutorService? = null

    /**
     * Queue of events to be processed by [lifeCycleProcessor] when [processEvents] is called.
     */
    private val eventQueueManager = LifecycleEventQueueManager(batchSize) { lifecycleEvent: LifeCycleEvent ->
        lifeCycleProcessor(lifecycleEvent, this)
    }

    /**
     * `true` if [processEvents] is executing.
     */
    private val isScheduled = AtomicBoolean(false)

    private val cleanupInProgress = AtomicBoolean(false)

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
        val executorService = this.executorService ?: return
        if (!isScheduled.getAndSet(true)) {
            executorService.submit(::processEvents)
        }
    }

    //: LifeCycleCoordinator

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
        val executorService = this.executorService
        if (executorService != null) {
            eventQueueManager.setTimer(
                key,
                executorService.schedule({ postEvent(onTime(key)) }, delay, TimeUnit.MILLISECONDS)
            )
        } else {
            logger.warn("Life-Cycle coordinator not running: timer set with key = $key is ignored!")
        }
    }

    //: LifeCycle

    /**
     * Return `true` in this coordinator is processing posted events.
     */
    override val isRunning: Boolean
        get() = lock.withLock { (executorService != null) }

    /**
     * Start this coordinator.
     *
     * **NOTE: events posted after last [stop] and before start are ignored.**
     *
     * @throws RejectedExecutionException if [executorService] can't schedule [processEvents].
     */
    @Throws(
        RejectedExecutionException::class
    )
    override fun start() {
        lock.withLock {
            if (executorService == null) {
                if (!cleanupInProgress.get()) {
                    cleanup()
                }
                executorService = Executors.newSingleThreadScheduledExecutor { runnable ->
                    val thread = Thread(runnable)
                    thread.isDaemon = true
                    thread
                }
                isScheduled.set(false)
                scheduleStartIfRequired()
            }
        }
    }

    private fun scheduleStartIfRequired() {
        val cleanup = cleanupInProgress.get()
        if (cleanup) {
            executorService?.submit(::scheduleStartIfRequired)
        } else {
            postEvent(StartEvent())
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
        val exec = lock.withLock {
            val exec = executorService
            executorService = null
            exec
        }

        exec?.apply {
            cleanupInProgress.set(true)
            eventQueueManager.postEvent(StopEvent())
            if (Thread.currentThread().id != executorThreadID) {
                submit { cleanup() }
                shutdown()
                if (!awaitTermination(timeout, TimeUnit.MILLISECONDS)) {
                    logger.warn("Stop: timeout after $timeout ms.")
                }
            } else {
                cleanup()
                shutdown()
            }
        }
    }

    private fun cleanup() {
        eventQueueManager.cleanup()
        cleanupInProgress.set(false)
    }

}