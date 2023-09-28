package net.corda.taskmanager.impl

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics
import java.util.concurrent.ScheduledExecutorService

internal class CordaExecutorServiceWrapper(
    private val name: String,
    private val metricPrefix: String,
    private val executor: ScheduledExecutorService,
    private val registry: MeterRegistry,
    private val delegate: ScheduledExecutorService = ExecutorServiceMetrics.monitor(registry, executor, name, metricPrefix)
) : ScheduledExecutorService by delegate