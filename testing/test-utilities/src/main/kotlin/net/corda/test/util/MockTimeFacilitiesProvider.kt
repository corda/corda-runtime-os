package net.corda.test.util

import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * A set of mocked APIs that make use of notions of time, where the time can be manually advanced programmatically
 * so that events happen deterministically.
 *
 * It contains:
 * - a mock [ScheduledExecutorService], where scheduled tasks will be executed only when time is advanced
 *   past the point they are supposed to be executed.
 * - a mock [Clock], which returns the time controlled by the user.
 *
 * @param initialTime the initial time set for all the mocked components.
 */
class MockTimeFacilitiesProvider(initialTime: Instant = Instant.ofEpochSecond(0)) {

    private val scheduledTasks: MutableList<Pair<Instant, Runnable>> = mutableListOf()
    private fun createFuture(timeAndTask: Pair<Instant, Runnable>): ScheduledFuture<*> {
        return mock {
            on { cancel(any()) } doAnswer {
                scheduledTasks.remove(timeAndTask)
            }
        }
    }
    private var now = initialTime

    val mockClock = mock<Clock> {
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

    /**
     * Advances time by the specified duration.
     * This will advance the time returned by the mock clock.
     * It will also iterate over all the tasks scheduled in the mock scheduled executor and execute the ones that were
     * scheduled to be executed before the current time.
     */
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