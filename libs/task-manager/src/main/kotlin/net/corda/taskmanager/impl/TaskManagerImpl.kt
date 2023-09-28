package net.corda.taskmanager.impl

import net.corda.taskmanager.TaskManager
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal class TaskManagerImpl(private val executorService: ScheduledExecutorService) : TaskManager {

    private var liveTaskCounts = ConcurrentHashMap<TaskManager.TaskType, Int>()

    override fun shutdown(): CompletableFuture<Void> {
        executorService.shutdown()
        // this completablefuture must not run on the executor service otherwise it'll never shut down.
        // runAsync runs this task in the fork join common pool.
        val shutdownFuture = CompletableFuture.runAsync {
            executorService.awaitTermination(100, TimeUnit.SECONDS)
        }
        return shutdownFuture
    }

    override fun <T> execute(type: TaskManager.TaskType, command: () -> T): CompletableFuture<T> {
        return when (type) {
            TaskManager.TaskType.SHORT_RUNNING -> executeShortRunningTask(command)
            TaskManager.TaskType.LONG_RUNNING -> executeLongRunningTask(command)
            TaskManager.TaskType.SCHEDULED -> executeScheduledTask(command)
        }
    }

    private fun <T> executeShortRunningTask(command: () -> T): CompletableFuture<T> {
        incrementTaskCount(TaskManager.TaskType.SHORT_RUNNING)
        return CompletableFuture.supplyAsync(
            { command().also { decrementTaskCount(TaskManager.TaskType.SHORT_RUNNING) } },
            executorService
        )
    }

    private fun <T> executeLongRunningTask(command: () -> T): CompletableFuture<T> {
        val uniqueId = UUID.randomUUID()
        val result = CompletableFuture<T>()
        incrementTaskCount(TaskManager.TaskType.LONG_RUNNING)
        thread(
            start = true,
            isDaemon = true,
            contextClassLoader = null,
            name = "Task Manager - $uniqueId",
            priority = -1,
        ) {
            result.complete(command())
            decrementTaskCount(TaskManager.TaskType.LONG_RUNNING)
        }
        return result
    }

    private fun <T> executeScheduledTask(command: () -> T, delay: Long, unit: TimeUnit): CompletableFuture<T> {
        incrementTaskCount(TaskManager.TaskType.SCHEDULED)
        val result = CompletableFuture<T>()
        executorService.schedule(
            {
                result.complete(command())
                decrementTaskCount(TaskManager.TaskType.SCHEDULED)
            },
            delay,
            unit
        )
        return result
    }

    private fun incrementTaskCount(type: TaskManager.TaskType) {
        liveTaskCounts.compute(type) { _, count -> if (count == null) 1 else count + 1 }
    }

    private fun decrementTaskCount(type: TaskManager.TaskType) {
        liveTaskCounts.computeIfPresent(type) { _, count -> count - 1 }
    }

    override fun execute(command: Runnable) {
        executorService.execute(command)
    }
}