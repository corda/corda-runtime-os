package net.corda.lifecycle.test.impl

import net.corda.lifecycle.LifecycleCoordinatorScheduler
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class TestLifecycleCoordinatorScheduler : LifecycleCoordinatorScheduler {
    override fun execute(task: Runnable) {
        task.run()
    }

    override fun timerSchedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> {
        TODO("Not yet implemented")
    }
}
