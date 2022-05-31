package net.corda.lifecycle.impl

import com.google.common.util.concurrent.ThreadFactoryBuilder
import net.corda.lifecycle.LifecycleCoordinatorScheduler
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class LifecycleCoordinatorSchedulerImpl : LifecycleCoordinatorScheduler {

    /**
     * The executor on which events are processed. Note that all events should be processed on an executor thread,
     * but they may be posted from any thread. Different events may be processed on different executor threads.
     *
     * The coordinator guarantees that the event processing task is only scheduled once. This means that event
     * processing is effectively single threaded in the sense that no event processing will happen concurrently.
     *
     * By sharing a thread pool among coordinators, it should be possible to reduce resource usage when in a stable
     * state.
     */
    private val executor = Executors.newCachedThreadPool(
        ThreadFactoryBuilder()
            .setNameFormat("lifecycle-coordinator-%d")
            .setDaemon(true)
            .build()
    )

    private val timerExecutor = Executors.newSingleThreadScheduledExecutor(
        ThreadFactoryBuilder()
            .setNameFormat("lifecycle-coordinator-timer-%d")
            .setDaemon(true)
            .build()
    )

    override fun execute(task: Runnable) {
        executor.execute(task)
    }

    override fun timerSchedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> {
        return timerExecutor.schedule(command, delay, unit)
    }
}