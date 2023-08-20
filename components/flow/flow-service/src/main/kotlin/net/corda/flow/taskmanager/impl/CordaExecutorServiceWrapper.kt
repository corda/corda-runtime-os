package net.corda.flow.taskmanager.impl

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.internal.TimedExecutorService
import java.util.concurrent.ExecutorService

class CordaExecutorServiceWrapper(
    private val name: String,
    private val metricPrefix: String,
    private val delegate: ExecutorService,
    private val registry: MeterRegistry,
    private val timedExecutorService: TimedExecutorService = TimedExecutorService(
        registry,
        delegate,
        name,
        metricPrefix,
        emptyList()
    )
) : ExecutorService by timedExecutorService