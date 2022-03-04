package net.corda.p2p.linkmanager.utilities

import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class MockExecutor {

    private val scheduledTasks: MutableList<Pair<Instant, Runnable>> = mutableListOf()
    private fun createFuture(timeAndTask: Pair<Instant, Runnable>): ScheduledFuture<*> {
        return mock {
            on { cancel(any()) } doAnswer {
                scheduledTasks.remove(timeAndTask)
            }
        }
    }
    private var now = Instant.ofEpochSecond(0)

    val clock = mock<Clock> {
        on { instant() } doAnswer { now }
    }

    val mockScheduledExecutor = mock<ScheduledExecutorService> {
        on { schedule(any(), any(), any()) } doAnswer {
            @Suppress("UNCHECKED_CAST")
            val task = it.arguments[0] as Runnable
            val delay = it.arguments[1] as Long
            val timeUnit = it.arguments[2] as TimeUnit
            val timeToExecute = now.plus(delay, timeUnit.toChronoUnit())
            val timeAndTask = timeToExecute to task
            scheduledTasks.add(timeAndTask)

            createFuture(timeAndTask)
        }
    }

    fun advanceTime(duration: Duration) {
        now = now.plusMillis(duration.toMillis())
        val iterator = scheduledTasks.iterator()
        val tasksToExecute = mutableListOf<Runnable>()
        while (iterator.hasNext()) {
            val (time, task) = iterator.next()
            if (time.isBefore(now) || time == now) {
                tasksToExecute.add(task)
                iterator.remove()
            }
        }

        // execute them outside the loop to avoid concurrent modification (when the tasks schedule tasks themselves)
        tasksToExecute.forEach { it.run() }
    }
}