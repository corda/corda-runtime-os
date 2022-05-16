package net.corda.lifecycle

import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

interface LifecycleCoordinatorScheduler {

    fun execute(task: Runnable)

    fun timerSchedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*>
}