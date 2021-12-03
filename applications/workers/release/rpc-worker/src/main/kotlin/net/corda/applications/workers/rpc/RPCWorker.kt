package net.corda.applications.workers.rpc

import net.corda.applications.workers.workercommon.CONFIG_DISABLE_HEALTH_MONITOR
import net.corda.applications.workers.workercommon.CONFIG_HEALTH_MONITOR_PORT
import net.corda.applications.workers.workercommon.HealthMonitor
import net.corda.applications.workers.workercommon.WorkerParams
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.osgi.api.Application
import net.corda.processors.rpc.RPCProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/** The worker for handling RPC requests. */
@Suppress("Unused")
@Component(service = [Application::class])
class RPCWorker @Activate constructor(
    @Reference(service = SmartConfigFactory::class)
    private val smartConfigFactory: SmartConfigFactory,
    @Reference(service = HealthMonitor::class)
    private val healthMonitor: HealthMonitor,
    @Reference(service = RPCProcessor::class)
    private val processor: RPCProcessor
) : Application {

    private companion object {
        private val logger = contextLogger()
    }

    /** Parses the arguments, then initialises and starts the [RPCProcessor]. */
    override fun startup(args: Array<String>) {
        logger.info("RPC worker starting.")

        val config = WorkerParams().parseArgs(args, smartConfigFactory)
        if (!config.getBoolean(CONFIG_DISABLE_HEALTH_MONITOR)) {
            healthMonitor.listen(config.getInt(CONFIG_HEALTH_MONITOR_PORT))
        }
        processor.config = config

        processor.start()
    }

    override fun shutdown() {
        logger.info("RPC worker stopping.")
        processor.stop()
        healthMonitor.stop()
    }
}