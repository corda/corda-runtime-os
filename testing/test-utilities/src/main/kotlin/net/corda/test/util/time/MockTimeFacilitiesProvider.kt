package net.corda.test.util.time

import net.corda.utilities.time.Clock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.mock
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
        val mockFuture = mock(ScheduledFuture::class.java)
        `when`(mockFuture.cancel(anyBoolean())).thenAnswer {
            scheduledTasks.remove(timeAndTask)
        }
        return mockFuture
    }

    val clock: TestClock = TestClock(initialTime)

    val mockScheduledExecutor: ScheduledExecutorService = mock(ScheduledExecutorService::class.java).also {
        `when`(it.schedule(any(), anyLong(), any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val task = invocation.arguments[0] as Runnable
            val delay = invocation.arguments[1] as Long
            val timeUnit = invocation.arguments[2] as TimeUnit
            val timeToExecute = clock.instant().plus(delay, timeUnit.toChronoUnit())
            val timeAndTask = timeToExecute to task
            scheduledTasks.add(timeAndTask)

            createFuture(timeAndTask)
        }
        `when`(it.execute(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as Runnable).run()
        }
    }

    /**
     * Advances time by the specified duration.
     * This will advance the time returned by the mock clock.
     * It will also iterate over all the tasks scheduled in the mock scheduled executor and execute the ones that were
     * scheduled to be executed before the current time.
     */
    fun advanceTime(duration: Duration) {
        clock.setTime(clock.instant().plusMillis(duration.toMillis()))
        val iterator = scheduledTasks.iterator()
        val tasksToExecute = mutableListOf<Pair<Instant, Runnable>>()
        while (iterator.hasNext()) {
            val (time, task) = iterator.next()
            if (time.isBefore(clock.instant()) || time == clock.instant()) {
                tasksToExecute.add(time to task)
                iterator.remove()
            }
        }

        // execute them outside the loop to avoid concurrent modification (when the tasks schedule tasks themselves)
        tasksToExecute.sortBy { it.first }
        tasksToExecute.forEach { it.second.run() }
    }
}
