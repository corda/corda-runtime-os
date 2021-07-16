package net.corda.lifecycle

import net.corda.v5.base.util.contextLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ScheduledFuture

/**
 * Manages the event queue for a lifecycle coordinator and associated timer state.
 *
 * This class ensures underlying state is updated atomically but is otherwise not thread safe. In particular, posting
 * events or starting timers while the manager is being cleaned up is non-deterministic - the event/timer may or may not
 * get cleaned up, depending on the timing.
 *
 * @param batchSize The number of events to process in a single call to `processEvents`.
 * @param processor The function to invoke to process each individual event.
 */
internal class LifecycleEventQueueManager(
    private val batchSize: Int,
    private val processor: (lifecycleEvent: LifeCycleEvent) -> Unit
) {

    companion object {
        private val logger = contextLogger()
    }

    private val eventQueue = ConcurrentLinkedDeque<LifeCycleEvent>()

    private val timerMap = ConcurrentHashMap<String, ScheduledFuture<*>>()

    val isEmpty: Boolean
        get() = eventQueue.isEmpty()

    /**
     * Post a new event to the queue.
     *
     * @param event The event to post
     */
    fun postEvent(event: LifeCycleEvent) {
        eventQueue.offer(event)
    }

    /**
     * Set a new timer to be managed.
     *
     * If a timer for the key already exists, then it is cancelled and a new timer set.
     *
     * @param key The key to use for this timer.
     * @param timer The timer to manage.
     */
    fun setTimer(key: String, timer: ScheduledFuture<*>) {
        cancelTimer(key)
        timerMap[key] = timer
    }

    /**
     * Cancel an existing timer.
     *
     * This will also remove any pending events from the queue for that timer. Note that calling cancel does not
     * guarantee an event for the timer will not be delivered - if cancel is called close to timer expiry it is possible
     * for a timer event to make it into the next batch to be processed just as it would be cleaned up.
     *
     * @param key The key of the timer to cancel.
     */
    fun cancelTimer(key: String) {
        timerMap[key]?.cancel(false)
        clearTimerEventsFromQueue(setOf(key))
        timerMap.remove(key)
    }

    /**
     * Cancel all outstanding timers and remove all remaining events from the queue.
     *
     * Events up to the next stop event are processed normally. Any events after this are discarded.
     */
    fun cleanup() {
        val keys = timerMap.keys().toList()
        timerMap.values.forEach { it.cancel(false) }
        clearTimerEventsFromQueue(keys)
        timerMap.clear()
        for (event in eventQueue) {
            processEvent(event)
            if (event is StopEvent) break
        }
        eventQueue.clear()
    }

    /**
     * Process a batch of events on the event queue.
     *
     * If the processor throws an error, then the error is posted back to the processor to give it the opportunity to
     * handle it. If the error is not marked as handled, or a subsequent error is thrown, the remainder of the batch is
     * delivered but this invocation of processEvents is considered to have failed.
     *
     * @return true if there were no errors, false if there was an unhandled error during processing
     */
    @Suppress("TooGenericExceptionCaught")
    fun processEvents(): Boolean {
        val batch = mutableListOf<LifeCycleEvent>()
        for (i in 0..batchSize) {
            val event = eventQueue.poll() ?: break
            batch.add(event)
        }

        return batch.map { processEvent(it) }.all { it }
    }

    /**
     * Process a single event.
     *
     * If an error occurs, a second event is delivered to give the processor a chance to handle the problem. An error
     * event is considered handled only if the `isHandled` flag is set by the processor to `true`.
     *
     * @param event The event to process.
     * @return true if the event was processed successfully or any errors were properly handled, false otherwise.
     */
    private fun processEvent(event: LifeCycleEvent) : Boolean {
        return try {
            processor(event)
            true
        } catch (e: Throwable) {
            val errorEvent = ErrorEvent(e)
            logger.warn("Life-Cycle coordinator caught ${e.message} starting ErrorEvent processing.", e)
            try {
                processor(errorEvent)
            } catch (e: Throwable) {
                logger.error("Life-Cycle coordinator caught unexpected ${e.message}" +
                        " during ErrorEvent processing. Will now stop coordinator!",
                    e)
                errorEvent.isHandled = false
            }
            errorEvent.isHandled
        }
    }

    /**
     * Remove any timer events from the event queue
     *
     * @param keys The set of keys to remove timer events for.
     */
    private fun clearTimerEventsFromQueue(keys: Collection<String>) {
        val eventQueueIterator = eventQueue.iterator()
        for (event in eventQueueIterator) {
            if (event is TimerEvent && event.key in keys) {
                eventQueueIterator.remove()
            }
        }
    }
}