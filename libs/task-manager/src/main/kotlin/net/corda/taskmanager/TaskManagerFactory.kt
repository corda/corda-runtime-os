package net.corda.taskmanager

import net.corda.taskmanager.impl.CordaExecutorServiceWrapper
import net.corda.taskmanager.impl.TaskManagerImpl
import net.corda.metrics.CordaMetrics
import java.util.UUID
import java.util.concurrent.Executors

object TaskManagerFactory {
    fun createThreadPoolTaskManager(
        threads: Int = 8,
        name: String = UUID.randomUUID().toString(),
        metricPrefix: String = "taskmanager."
    ): TaskManager {
        return TaskManagerImpl(
            CordaExecutorServiceWrapper(
                name,
                "corda.$metricPrefix",
                Executors.newFixedThreadPool(threads),
                CordaMetrics.registry
            )
        )
    }
}