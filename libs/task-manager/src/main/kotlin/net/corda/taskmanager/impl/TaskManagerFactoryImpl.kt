package net.corda.taskmanager.impl

import com.google.common.util.concurrent.ThreadFactoryBuilder
import net.corda.metrics.CordaMetrics
import net.corda.taskmanager.TaskManager
import net.corda.taskmanager.TaskManagerFactory
import java.util.concurrent.Executors

internal object TaskManagerFactoryImpl : TaskManagerFactory {

    override fun createThreadPoolTaskManager(
        name: String,
        threadName: String,
        metricPrefix: String,
        threads: Int,
    ): TaskManager {
        return TaskManagerImpl(
            name = name,
            longRunningThreadName = "$threadName-long-running-thread",
            executorService = CordaExecutorServiceWrapper(
                name,
                "corda.taskmanager.$metricPrefix.",
                Executors.newScheduledThreadPool(
                    threads,
                    ThreadFactoryBuilder()
                        .setNameFormat("$threadName-thread-%d")
                        .setDaemon(false)
                        .build()
                ),
                CordaMetrics.registry
            )
        )
    }
}