package net.corda.lifecycle.impl

import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
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

    /**
     * The set of registrations to post status updates to when this coordinator's status changes.
     */
    val registrations: MutableSet<Registration> = ConcurrentHashMap.newKeySet()

    /**
     * The set of registrations this coordinator has on other groups of registrations.
     *
     * This is used to ensure that an UP event is delivered to this coordinator if the registration is UP at coordinator
     * start time.
     */
    val trackedRegistrations: MutableSet<Registration> = ConcurrentHashMap.newKeySet()

    @Volatile
    var isRunning: Boolean = false

    @Volatile
    var status: LifecycleStatus = LifecycleStatus.DOWN

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
        timerMap.remove(key)?.cancel(false)
    }

    /**
     * Checks to see if a timer is currently running.
     *
     * @param key The timer key to check
     * @return True if the timer is running
     */
    fun isTimerRunning(key: String): Boolean {
        return key in timerMap.keys
    }

    /**
     * Creates the next batch of events for processing and removes those events from the queue.
     *
     * @return The next batch of events
     */
    fun nextBatch(): List<LifecycleEvent> {
        val batch = mutableListOf<LifecycleEvent>()
        for (i in 0 until batchSize) {
            val event = eventQueue.poll() ?: break
            batch.add(event)
        }
        return batch
    }

    /**
     * Get whether events are queued.
     *
     * @return True if there are events to process, false otherwise.
     */
    fun eventsQueued(): Boolean {
        return !eventQueue.isEmpty()
    }

    /**
     * Cancel al the timers own by the state manager.
     */
    fun cancelAllTimer() {
        timerMap.values.forEach {
            it.cancel(true)
        }
        timerMap.clear()
    }

    /**
     * Returns whether this coordinator has any open registrations, either on other coordinators or with other
     * coordinators on it.
     *
     * Used to ensure that the coordinator can only be closed if there are no registrations.
     *
     * @return True if there are no registrations, false otherwise.
     */
    fun registrationsEmpty(): Boolean {
        return registrations.isEmpty() && trackedRegistrations.isEmpty()
    }
}
