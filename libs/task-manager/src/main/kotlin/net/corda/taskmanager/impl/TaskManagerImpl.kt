package net.corda.taskmanager.impl

import net.corda.taskmanager.TaskManager
import net.corda.utilities.VisibleForTesting
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal class TaskManagerImpl(
    private val longRunningThreadName: String,
    private val executorService: ScheduledExecutorService
) : TaskManager {

    enum class Type {
        SHORT_RUNNING, LONG_RUNNING, SCHEDULED
    }

    @VisibleForTesting
    val liveTaskCounts = ConcurrentHashMap<Type, Int>()

    override fun <T> executeShortRunningTask(command: () -> T): CompletableFuture<T> {
        incrementTaskCount(Type.SHORT_RUNNING)
        return CompletableFuture.supplyAsync(
            { command() },
            executorService
        ).whenComplete { _, _ -> decrementTaskCount(Type.SHORT_RUNNING) }
    }

    override fun <T> executeLongRunningTask(command: () -> T): CompletableFuture<T> {
        val uniqueId = UUID.randomUUID()
        val result = CompletableFuture<T>()
        incrementTaskCount(Type.LONG_RUNNING)
        thread(
            start = true,
            isDaemon = true,
            contextClassLoader = null,
            name = "$longRunningThreadName-$uniqueId",
            priority = -1,
        ) {
            try {
                result.complete(command())
            } catch (t: Throwable) {
                result.completeExceptionally(t)
            }
        }
        return result.whenComplete { _, _ -> decrementTaskCount(Type.LONG_RUNNING) }
    }

    override fun <T> executeScheduledTask(command: () -> T, delay: Long, unit: TimeUnit): CompletableFuture<T> {
        incrementTaskCount(Type.SCHEDULED)
        val result = CompletableFuture<T>()
        executorService.schedule(
            {
                try {
                    result.complete(command())
                } catch (t: Throwable) {
                    result.completeExceptionally(t)
                }
            },
            delay,
            unit
        )
        return result.whenComplete { _, _ -> decrementTaskCount(Type.SCHEDULED) }
    }

    override fun execute(command: Runnable) {
        executorService.execute(command)
    }

    override fun shutdown(): CompletableFuture<Void> {
        executorService.shutdown()
        // This [CompletableFuture] must not run on the executor service otherwise it'll never shut down.
        // [runAsync] runs this task in the fork join common pool.
        val shutdownFuture = CompletableFuture.runAsync {
            executorService.awaitTermination(100, TimeUnit.SECONDS)
        }
        return shutdownFuture
    }

    private fun incrementTaskCount(type: Type) {
        liveTaskCounts.compute(type) { _, count -> if (count == null) 1 else count + 1 }
    }

    private fun decrementTaskCount(type: Type) {
        liveTaskCounts.computeIfPresent(type) { _, count -> count - 1 }
    }
}