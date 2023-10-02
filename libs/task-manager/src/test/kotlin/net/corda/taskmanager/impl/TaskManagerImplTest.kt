package net.corda.taskmanager.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.ExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class TaskManagerImplTest {

    private companion object {
        const val RESULT = 1
    }

    private val executorService = mock<ScheduledExecutorService>()
    private val captor = argumentCaptor<Runnable>()
    private val taskManager = TaskManagerImpl("", "", executorService)

    @Test
    fun `executeShortRunningTask increments the task count, runs the task and decrements the task count when finished`() {
        whenever(executorService.execute(captor.capture())).then { captor.firstValue.run() }
        val result = taskManager.executeShortRunningTask {
            assertThat(taskManager.liveTaskCounts).containsExactlyEntriesOf(
                mapOf(TaskManagerImpl.Type.SHORT_RUNNING to 1)
            )
            RESULT
        }
        assertThat(result.get()).isEqualTo(RESULT)
        assertThat(taskManager.liveTaskCounts).containsExactlyEntriesOf(
            mapOf(TaskManagerImpl.Type.SHORT_RUNNING to 0)
        )
    }

    @Test
    fun `executeShortRunningTask increments the task count and decrements the task count when the task fails`() {
        whenever(executorService.execute(captor.capture())).then { captor.firstValue.run() }
        val result = taskManager.executeShortRunningTask {
            assertThat(taskManager.liveTaskCounts).containsExactlyEntriesOf(
                mapOf(TaskManagerImpl.Type.SHORT_RUNNING to 1)
            )
            throw IllegalStateException("fails")
        }
        assertThatThrownBy { result.get() }
            .isExactlyInstanceOf(ExecutionException::class.java)
            .hasCauseExactlyInstanceOf(IllegalStateException::class.java)
        assertThat(taskManager.liveTaskCounts).containsExactlyEntriesOf(
            mapOf(TaskManagerImpl.Type.SHORT_RUNNING to 0)
        )
    }

    @Test
    fun `executeLongRunningTask increments the task count, runs the task and decrements the task count when finished`() {
        val result = taskManager.executeLongRunningTask {
            assertThat(taskManager.liveTaskCounts).containsExactlyEntriesOf(
                mapOf(TaskManagerImpl.Type.LONG_RUNNING to 1)
            )
            RESULT
        }
        assertThat(result.get()).isEqualTo(RESULT)
        assertThat(taskManager.liveTaskCounts).containsExactlyEntriesOf(
            mapOf(TaskManagerImpl.Type.LONG_RUNNING to 0)
        )
    }

    @Test
    fun `executeLongRunningTask increments the task count and decrements the task count when the task fails`() {
        val result = taskManager.executeLongRunningTask {
            assertThat(taskManager.liveTaskCounts).containsExactlyEntriesOf(
                mapOf(TaskManagerImpl.Type.LONG_RUNNING to 1)
            )
            throw IllegalStateException("fails")
        }
        assertThatThrownBy { result.get() }
            .isExactlyInstanceOf(ExecutionException::class.java)
            .hasCauseExactlyInstanceOf(IllegalStateException::class.java)
        assertThat(taskManager.liveTaskCounts).containsExactlyEntriesOf(
            mapOf(TaskManagerImpl.Type.LONG_RUNNING to 0)
        )
    }

    @Test
    fun `executeScheduledTask increments the task count, runs the task and decrements the task count when finished`() {
        whenever(executorService.schedule(captor.capture(), any<Long>(), any<TimeUnit>())).then {
            captor.firstValue.run()
            mock<ScheduledFuture<Any>>()
        }
        val result = taskManager.executeScheduledTask(
            {
                assertThat(taskManager.liveTaskCounts).containsExactlyEntriesOf(
                    mapOf(TaskManagerImpl.Type.SCHEDULED to 1)
                )
                RESULT
            },
            1,
            TimeUnit.SECONDS
        )
        assertThat(result.get()).isEqualTo(RESULT)
        assertThat(taskManager.liveTaskCounts).containsExactlyEntriesOf(
            mapOf(TaskManagerImpl.Type.SCHEDULED to 0)
        )
    }

    @Test
    fun `executeScheduledTask increments the task count and decrements the task count when the task fails`() {
        whenever(executorService.schedule(captor.capture(), any<Long>(), any<TimeUnit>())).then {
            captor.firstValue.run()
            mock<ScheduledFuture<Any>>()
        }
        val result = taskManager.executeScheduledTask(
            {
                assertThat(taskManager.liveTaskCounts).containsExactlyEntriesOf(
                    mapOf(TaskManagerImpl.Type.SCHEDULED to 1)
                )
                throw IllegalStateException("fails")
            },
            1,
            TimeUnit.SECONDS
        )
        assertThatThrownBy { result.get() }
            .isExactlyInstanceOf(ExecutionException::class.java)
            .hasCauseExactlyInstanceOf(IllegalStateException::class.java)
        assertThat(taskManager.liveTaskCounts).containsExactlyEntriesOf(
            mapOf(TaskManagerImpl.Type.SCHEDULED to 0)
        )
    }

    @Test
    fun `shutdown terminates executor service`() {
        taskManager.shutdown().get()
        verify(executorService).awaitTermination(any(), any())
    }
}