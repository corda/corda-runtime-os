package net.corda.lifecycle.impl

import net.corda.lifecycle.LifecycleCoordinatorScheduler
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class LifecycleCoordinatorSchedulerImpl(
    private val executor: ExecutorService,
    private val timerExecutor: ScheduledExecutorService
) : LifecycleCoordinatorScheduler {

    override fun execute(task: Runnable) {
        executor.execute(task)
    }

    override fun timerSchedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> {
        return timerExecutor.schedule(command, delay, unit)
    }
}
