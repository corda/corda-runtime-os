package net.corda.applications.workers.flow

import net.corda.applications.workers.workercommon.HealthMonitor
import net.corda.applications.workers.workercommon.StandardWorkerParams
import net.corda.applications.workers.workercommon.getAdditionalConfig
import net.corda.applications.workers.workercommon.getParams
import net.corda.applications.workers.workercommon.setUpHealthMonitor
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.osgi.api.Application
import net.corda.processors.flow.FlowProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import picocli.CommandLine.Mixin

/** The worker for handling flows. */
@Suppress("Unused")
@Component(service = [Application::class])
class FlowWorker @Activate constructor(
    @Reference(service = SmartConfigFactory::class)
    private val smartConfigFactory: SmartConfigFactory,
    @Reference(service = HealthMonitor::class)
    private val healthMonitor: HealthMonitor,
    @Reference(service = FlowProcessor::class)
    private val processor: FlowProcessor
) : Application {

    private companion object {
        private val logger = contextLogger()
    }

    /** Parses the arguments, then initialises and starts the [processor]. */
    override fun startup(args: Array<String>) {
        logger.info("Flow worker starting.")

        val params = getParams(args, FlowWorkerParams())
        setUpHealthMonitor(healthMonitor, params.standardWorkerParams)

        val config = getAdditionalConfig(params.standardWorkerParams, smartConfigFactory)
        processor.start(params.standardWorkerParams.instanceId, config)
    }

    override fun shutdown() {
        logger.info("Flow worker stopping.")
        processor.stop()
        healthMonitor.stop()
    }
}

/** Additional parameters for the flow worker are added here. */
private class FlowWorkerParams {
    @Mixin
    var standardWorkerParams = StandardWorkerParams()
}