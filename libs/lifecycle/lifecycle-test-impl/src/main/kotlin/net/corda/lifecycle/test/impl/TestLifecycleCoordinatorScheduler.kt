package net.corda.lifecycle.test.impl

import net.corda.lifecycle.LifecycleCoordinatorScheduler
import java.util.concurrent.Delayed
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class TestLifecycleCoordinatorScheduler : LifecycleCoordinatorScheduler {

    override fun execute(task: Runnable) {
        task.run()
    }

    override fun timerSchedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> {
        command.run()
        return object : ScheduledFuture<String> {
            override fun compareTo(other: Delayed?): Int {
                throw NotImplementedError()
            }

            override fun getDelay(p0: TimeUnit): Long {
                return 0
            }

            override fun cancel(p0: Boolean): Boolean {
                return true
            }

            override fun isCancelled(): Boolean {
                return false
            }

            override fun isDone(): Boolean {
                return true
            }

            override fun get(): String {
                return ""
            }

            override fun get(p0: Long, p1: TimeUnit): String {
                return ""
            }
        }
    }
}
