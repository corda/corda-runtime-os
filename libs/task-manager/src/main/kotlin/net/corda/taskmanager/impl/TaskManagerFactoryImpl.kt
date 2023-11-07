package net.corda.taskmanager.impl

import net.corda.metrics.CordaMetrics
import net.corda.taskmanager.TaskManager
import net.corda.taskmanager.TaskManagerFactory
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicLong

internal object TaskManagerFactoryImpl : TaskManagerFactory {

    override fun createThreadPoolTaskManager(
        name: String,
        threadName: String,
        threads: Int,
    ): TaskManager {
        return TaskManagerImpl(
            name = name,
            longRunningThreadName = "$threadName-long-running-thread",
            executorService = CordaExecutorServiceWrapper(
                name,
                "corda.taskmanager.",
                Executors.newFixedThreadPool(
                    threads,
                    threadFactory(threadName)
                ),
                CordaMetrics.registry
            )
        )
    }

    private fun threadFactory(threadName: String): ThreadFactory {
        val backingThreadFactory = Executors.defaultThreadFactory()
        val count = AtomicLong(0)
        return ThreadFactory { runnable ->
            backingThreadFactory.newThread(runnable).apply {
                setName(String.format(Locale.ROOT, "$threadName-thread-%d", count.getAndIncrement()))
                setDaemon(false)
            }
        }
    }
}