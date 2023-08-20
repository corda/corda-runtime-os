package net.corda.taskmanager.impl

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics
import java.util.concurrent.ExecutorService

internal class CordaExecutorServiceWrapper(
    private val name: String,
    private val metricPrefix: String,
    private val executor: ExecutorService,
    private val registry: MeterRegistry,
    private val delegate: ExecutorService = ExecutorServiceMetrics.monitor(registry, executor, name, metricPrefix)
) : ExecutorService by delegate