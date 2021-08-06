package net.corda.lifecycle.impl

import net.corda.lifecycle.LifecycleEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ScheduledFuture

/**
 * Manages the event queue for a lifecycle coordinator and associated timer state.
 *
 * This class ensures that updates to the state is thread safe.
 *
 * @param batchSize The number of events to process in a single call to `processEvents`
 */
internal class LifecycleStateManager(
    private val batchSize: Int
) {

    private val eventQueue = ConcurrentLinkedDeque<LifecycleEvent>()

    private val timerMap = ConcurrentHashMap<String, ScheduledFuture<*>>()

    @Volatile
    var isRunning: Boolean = false

    /**
     * Post a new event to the queue.
     *
     * @param event The event to post
     */
    fun postEvent(event: LifecycleEvent) {
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
        timerMap.remove(key)
    }

    /**
     * Checks to see if a timer is currently running.
     *
     * @param key The timer key to check
     */
    fun isTimerRunning(key: String): Boolean {
        return key in timerMap.keys
    }

    /**
     * Creates the next batch of events for processing and removes those events from the queue.
     */
    fun nextBatch(): List<LifecycleEvent> {
        val batch = mutableListOf<LifecycleEvent>()
        for (i in 0 until batchSize) {
            val event = eventQueue.poll() ?: break
            batch.add(event)
        }
        return batch
    }
}