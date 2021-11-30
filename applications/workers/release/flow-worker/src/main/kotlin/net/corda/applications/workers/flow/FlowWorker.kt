package net.corda.applications.workers.flow

import net.corda.applications.workers.workercommon.HealthProvider
import net.corda.applications.workers.workercommon.Worker
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.osgi.api.Application
import net.corda.processors.flow.FlowProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * The [Worker] for handling flows.
 *
 * @param smartConfigFactory The factory for creating a `SmartConfig` object from the worker's configuration.
 */
@Suppress("Unused")
@Component(service = [Application::class])
class FlowWorker @Activate constructor(
    @Reference(service = SmartConfigFactory::class)
    smartConfigFactory: SmartConfigFactory
) : Worker(smartConfigFactory) {
    private companion object {
        private val logger = contextLogger()
    }

    /** Starts the [FlowProcessor], passing in the [healthProvider] and [workerConfig]. */
    override fun startup(healthProvider: HealthProvider, workerConfig: SmartConfig) {
        logger.info("Flow worker starting")
        FlowProcessor().startup(healthProvider, workerConfig)
    }
}