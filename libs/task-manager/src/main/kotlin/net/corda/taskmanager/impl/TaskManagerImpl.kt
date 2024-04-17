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

    private class BatchRaceException : Exception("Batch is already close")

    private val shortRunningTaskBatches = ConcurrentHashMap<Any, Batch>()

    class BatchComparator : Comparator<Runnable> {
        override fun compare(b1: Runnable, b2: Runnable): Int {
            if (b1 !is Batch || b2 !is Batch) throw IllegalStateException("Was expecting Batch")
            return (b1.priority - b2.priority).sign
        }

    }

    private data class Step(
        val command: () -> Any,
        val future: CompletableFuture<Any>,
        val persistedFuture: CompletableFuture<Unit>
    )

    inner class Batch(private val key: Any, val priority: Long) : Runnable {
        private val queue = LinkedBlockingDeque<Step>()
        private var started = false
        private var closed = false

        @Suppress("UNCHECKED_CAST")
        @Synchronized
        fun <T : Any> addToBatch(
            persistedFuture: CompletableFuture<Unit>,
            forceFirst: Boolean,
            command: () -> T
        ): CompletableFuture<T> {
            val future = CompletableFuture<Any>()
            if (forceFirst) {
                queue.addFirst(Step(command, future, persistedFuture))
                started = true
                executorService.execute(this)
            } else {
                if (closed) throw BatchRaceException()
                val isFirst = queue.isEmpty() && !started
                queue.add(Step(command, future, persistedFuture))
                if (isFirst) {
                    started = true
                    executorService.execute(this)
                }
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
            val (nextCommand, nextFuture, persistedFuture) = queue.poll() ?: return
            // Only submit the next in the queue once this one has made it all the way to persistence
            persistedFuture.whenComplete { _, _ ->
                if (!close()) {
                    executorService.execute(this)
                }
            }
            executeStep(nextCommand, nextFuture)
        }

        override fun run() {
            runNextCommand()
        }

        private fun executeStep(
            command: () -> Any,
            future: CompletableFuture<Any>
        ) {
            if (!future.isCancelled) {
                try {
                    val commandResult = command()
                    future.complete(commandResult)
                } catch (e: Throwable) {
                    future.completeExceptionally(e)
                }
            }
        }
    }

    override fun <T : Any> executeShortRunningTask(
        key: Any,
        priority: Long,
        persistedFuture: CompletableFuture<Unit>,
        forceFirst: Boolean,
        command: () -> T
    ): CompletableFuture<T> {
        val start = System.nanoTime()
        incrementTaskCount(Type.SHORT_RUNNING)
        while (true) {
            val batch = shortRunningTaskBatches.computeIfAbsent(key) {
                Batch(key, priority)
            }
            try {
                return batch.addToBatch(persistedFuture, forceFirst) {
                    try {
                        command().also {
                            recordCompletion(start, Type.SHORT_RUNNING)
                        }
                    } catch (e: Exception) {
                        recordCompletion(start, Type.SHORT_RUNNING)
                        throw e
                    }
                }
            } catch (_: BatchRaceException) {
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