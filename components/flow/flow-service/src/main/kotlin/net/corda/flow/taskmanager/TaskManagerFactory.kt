package net.corda.flow.taskmanager

import io.micrometer.core.instrument.MeterRegistry
import net.corda.flow.taskmanager.impl.CordaExecutorServiceWrapper
import net.corda.flow.taskmanager.impl.TaskManagerImpl
import net.corda.metrics.CordaMetrics
import java.util.UUID
import java.util.concurrent.Executors

object TaskManagerFactory {
    fun createThreadPoolTaskManager(
        threads: Int = 8,
        name: String = UUID.randomUUID().toString(),
        metricPrefix: String = "taskmanager.",
        meterRegistry: MeterRegistry = CordaMetrics.registry
    ): TaskManager {
        return TaskManagerImpl(
            CordaExecutorServiceWrapper(
                name,
                "corda.$metricPrefix",
                Executors.newFixedThreadPool(threads),
                meterRegistry
            )
        )
    }
}