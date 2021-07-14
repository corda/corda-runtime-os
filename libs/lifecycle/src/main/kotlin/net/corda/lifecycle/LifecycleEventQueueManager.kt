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

    fun cancelTimer(key: String) {
        timerMap[key]?.cancel(false)
        clearTimerEventsFromQueue(setOf(key))
        timerMap.remove(key)
    }

    fun cleanup() {
        val keys = timerMap.keys().toList()
        timerMap.values.forEach { it.cancel(false) }
        clearTimerEventsFromQueue(keys)
        timerMap.clear()
        for (event in eventQueue) {
            processor(event)
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

        var succeeded = true
        for (event in batch) {
            try {
                processor(event)
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
                if (!errorEvent.isHandled) {
                    succeeded = false
                }
            }
        }
        return succeeded
    }

    private fun clearTimerEventsFromQueue(keys: Collection<String>) {
        val eventQueueIterator = eventQueue.iterator()
        for (event in eventQueueIterator) {
            if (event is TimerEvent && event.key in keys) {
                eventQueueIterator.remove()
            }
        }
    }
}