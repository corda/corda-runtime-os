package net.corda.taskmanager

import net.corda.taskmanager.impl.CordaExecutorServiceWrapper
import net.corda.taskmanager.impl.TaskManagerImpl
import net.corda.metrics.CordaMetrics
import java.util.UUID
import java.util.concurrent.Executors

object TaskManagerFactory {
    fun createThreadPoolTaskManager(
        name: String,
        threads: Int,
        metricPrefix: String = "taskmanager."
    ): TaskManager {
        return TaskManagerImpl(
            CordaExecutorServiceWrapper(
                name,
                "corda.$metricPrefix",
                Executors.newScheduledThreadPool(threads),
                CordaMetrics.registry
            )
        )
    }
}