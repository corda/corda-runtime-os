package net.corda.lifecycle

import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Defines how the [LifecycleCoordinator] will execute tasks in its event queue.
 * This could be, for example, on a different thread via an executor, or immediately
 * on the same thread.
 */
interface LifecycleCoordinatorScheduler {
    /**
     * Execute the given [task]
     */
    fun execute(task: Runnable)

    /**
     * Execute the given [command] at a future point after the given [delay]
     */
    fun timerSchedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*>
}
