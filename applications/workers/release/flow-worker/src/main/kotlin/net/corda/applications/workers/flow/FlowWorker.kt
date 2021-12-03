package net.corda.applications.workers.flow

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
// TODO - Joel - Provide config option to change healthcheck port.

/** The worker for handling flows. */
@Suppress("Unused")
@Component(service = [Application::class])
class FlowWorker @Activate constructor(
    // TODO - Joel - Inject this directly into some WorkerParamsService?
    @Reference(service = SmartConfigFactory::class)
    private val smartConfigFactory: SmartConfigFactory,
    @Reference(service = HealthMonitor::class)
    healthMonitor: HealthMonitor,
    @Reference(service = FlowProcessor::class)
    private val processor: FlowProcessor
) : Application {

    init {
        // TODO - Joel - Temporarily used to force loading of health monitor. Problem will go away once we allow health
        //  monitor to be switched on/off.
        println(healthMonitor)
    }

    private companion object {
        private val logger = contextLogger()
    }

    /** Parses the arguments, then initialises and starts the [FlowProcessor]. */
    override fun startup(args: Array<String>) {
        logger.info("Flow worker starting.")
        processor.config = WorkerParams().parseArgs(args, smartConfigFactory)
        processor.start()
    }

    override fun shutdown() {
        logger.info("Flow worker stopping.")
        processor.stop()
    }
}