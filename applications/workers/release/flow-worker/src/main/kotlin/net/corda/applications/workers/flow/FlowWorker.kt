package net.corda.applications.workers.flow

import net.corda.applications.workers.workercommon.CONFIG_HEALTH_MONITOR_PORT
import net.corda.applications.workers.workercommon.HealthMonitor
import net.corda.applications.workers.workercommon.WorkerParams
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.osgi.api.Application
import net.corda.processors.flow.FlowProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

// TODO - Joel - Create all-in-one worker - a worker that bootstraps multiple processors.
// TODO - Joel - Provide config option to disable healthcheck.

/** The worker for handling flows. */
@Suppress("Unused")
@Component(service = [Application::class])
class FlowWorker @Activate constructor(
    // TODO - Joel - Inject this directly into some WorkerParamsService?
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

    /** Parses the arguments, then initialises and starts the [FlowProcessor]. */
    override fun startup(args: Array<String>) {
        logger.info("Flow worker starting.")
        val config = WorkerParams().parseArgs(args, smartConfigFactory)
        healthMonitor.listen(config.getInt(CONFIG_HEALTH_MONITOR_PORT))
        // TODO - Joel - Only extra config should be passed to the processor.
        processor.config = config
        processor.start()
    }

    override fun shutdown() {
        logger.info("Flow worker stopping.")
        processor.stop()
        healthMonitor.stop()
    }
}