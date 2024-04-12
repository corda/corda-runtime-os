package net.corda.taskmanager.impl

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Timer
import net.corda.metrics.CordaMetrics
import net.corda.taskmanager.TaskManager
import net.corda.utilities.VisibleForTesting
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.sign

internal class TaskManagerImpl(
    private val name: String,
    private val longRunningThreadName: String,
    private val executorService: ExecutorService
) : TaskManager {

    enum class Type {
        SHORT_RUNNING, LONG_RUNNING
    }

    @VisibleForTesting
    val liveTaskCounts = ConcurrentHashMap<Type, Int>()

    private val shortRunningTaskCompletionTime = taskCompletionMeter(Type.SHORT_RUNNING)
    private val longRunningTaskCompletionTime = taskCompletionMeter(Type.LONG_RUNNING)
    private val shortRunningTaskGauge = liveTaskGauge(Type.SHORT_RUNNING)
    private val longRunningTaskGauge = liveTaskGauge(Type.LONG_RUNNING)

    private class BatchRaceException() : Exception("Batch is already close")

    private val shortRunningTaskBatches = ConcurrentHashMap<Any, Batch>()

    class BatchComparator : Comparator<Runnable> {
        override fun compare(b1: Runnable, b2: Runnable): Int {
            if(b1 !is Batch || b2 !is Batch) throw IllegalStateException("Was expecting Batch")
            return (b1.priority - b2.priority).sign
        }

    }
    inner class Batch(private val key: Any, val priority: Long) : Runnable {
        private val queue = LinkedBlockingDeque<Pair<() -> Any, CompletableFuture<Any>>>()
        private var started = false
        private var closed = false

        inner class BatchCompletableFuture<T> : CompletableFuture<T>() {
            override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
                val cancelled = super.cancel(mayInterruptIfRunning)
                interruptIfRunning(this)
                return cancelled
            }
        }

        @Suppress("UNCHECKED_CAST")
        @Synchronized
        fun <T : Any> addToBatch(command: () -> T): CompletableFuture<T> {
            if (closed) throw BatchRaceException()
            val isFirst = queue.isEmpty() && !started
            val future = BatchCompletableFuture<Any>()
            queue.add(command to future)
            if (isFirst) {
                started = true
                executorService.execute(this)
            }
            return future as CompletableFuture<T>
        }

        @Synchronized
        private fun close(): Boolean {
            if (queue.isEmpty()) {
                closed = true
                shortRunningTaskBatches.remove(key)
            }
            return closed
        }

        private fun runNextCommand() {
            val (nextCommand, nextFuture) = queue.poll() ?: return
            executeStep(nextCommand, nextFuture)
        }

        override fun run() {
            runNextCommand()
            if (!close()) {
                executorService.execute(this)
            }
        }

        @Volatile
        var currentFuture: CompletableFuture<Any>? = null
        @Volatile
        var currentThread: Thread? = null

        @Synchronized
        private fun interruptIfRunning(future: CompletableFuture<*>) {
            if (currentFuture == future) {
                currentThread?.interrupt()
            }
        }

        @Synchronized
        private fun clearCurrentFuture() {
            currentFuture = null
            currentThread = null
            Thread.interrupted()
        }

        private fun executeStep(command: () -> Any, future: CompletableFuture<Any>) {
            try {
                currentFuture = future
                currentThread = Thread.currentThread()
                if (!future.isCancelled) {
                    val commandResult = command()
                    clearCurrentFuture()
                    future.complete(commandResult)
                }
            } catch (e: Throwable) {
                clearCurrentFuture()
                future.completeExceptionally(e)
            }
        }
    }

    override fun <T : Any> executeShortRunningTask(key: Any, priority: Long, command: () -> T): CompletableFuture<T> {
        val start = System.nanoTime()
        incrementTaskCount(Type.SHORT_RUNNING)
        while(true) {
            val batch = shortRunningTaskBatches.computeIfAbsent(key) {
                Batch(key, priority)
            }
            try {
                return batch.addToBatch {
                    try {
                        command().also {
                            recordCompletion(start, Type.SHORT_RUNNING)
                        }
                    } catch (e: Exception) {
                        recordCompletion(start, Type.SHORT_RUNNING)
                        throw e
                    }
                }
            } catch(e: BatchRaceException) {
            }
        }
    }

    override fun <T> executeLongRunningTask(command: () -> T): CompletableFuture<T> {
        val start = System.nanoTime()
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
        return result.whenComplete { _, _ ->
            recordCompletion(start, Type.LONG_RUNNING)
        }
    }

    override fun execute(command: Runnable) {
        executorService.execute(command)
    }

    override fun shutdown(): CompletableFuture<Void> {
        executorService.shutdown()
        CordaMetrics.registry.remove(shortRunningTaskGauge)
        CordaMetrics.registry.remove(longRunningTaskGauge)
        // This [CompletableFuture] must not run on the executor service otherwise it'll never shut down.
        // [runAsync] runs this task in the fork join common pool.
        val shutdownFuture = CompletableFuture.runAsync {
            executorService.awaitTermination(100, TimeUnit.SECONDS)
        }
        return shutdownFuture
    }

    private fun recordCompletion(start: Long, type: Type) {
        when (type) {
            Type.SHORT_RUNNING -> shortRunningTaskCompletionTime.record(System.nanoTime() - start, TimeUnit.NANOSECONDS)
            Type.LONG_RUNNING -> longRunningTaskCompletionTime.record(System.nanoTime() - start, TimeUnit.NANOSECONDS)
        }
        decrementTaskCount(type)
    }

    private fun incrementTaskCount(type: Type) {
        liveTaskCounts.compute(type) { _, count -> if (count == null) 1 else count + 1 }
    }

    private fun decrementTaskCount(type: Type) {
        liveTaskCounts.computeIfPresent(type) { _, count -> count - 1 }
    }

    private fun taskCompletionMeter(type: Type): Timer {
        return CordaMetrics.Metric.TaskManager.TaskCompletionTime.builder()
            .withTag(CordaMetrics.Tag.TaskManagerName, name)
            .withTag(CordaMetrics.Tag.TaskType, type.name)
            .build()
    }

    private fun liveTaskGauge(type: Type): Gauge {
        return CordaMetrics.Metric.TaskManager.LiveTasks { liveTaskCounts[type] ?: 0 }.builder()
            .withTag(CordaMetrics.Tag.TaskManagerName, name)
            .withTag(CordaMetrics.Tag.TaskType, type.name)
            .build()
    }
}