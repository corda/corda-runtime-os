package net.corda.taskmanager.impl

import net.corda.taskmanager.TaskManager
import net.corda.taskmanager.TaskManagerFactory
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
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
            executorService = ThreadPoolExecutor(
                threads, threads,
                0L, TimeUnit.MILLISECONDS,
                PriorityBlockingQueue<Runnable>(threads * 4, TaskManagerImpl.BatchComparator()),
                threadFactory(threadName)
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