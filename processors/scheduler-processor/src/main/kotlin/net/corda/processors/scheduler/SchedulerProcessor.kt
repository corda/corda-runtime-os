package net.corda.processors.scheduler

import net.corda.libs.configuration.SmartConfig

interface SchedulerProcessor {
    /**
     * Starts performing the work of the DB worker.
     *
     * @throws DBProcessorException If the cluster database cannot be connected to.
     */
    fun start(bootConfig: SmartConfig)

    fun stop()
}