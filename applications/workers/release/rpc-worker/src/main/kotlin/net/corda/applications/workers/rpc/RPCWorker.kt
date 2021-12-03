package net.corda.applications.workers.rpc

import net.corda.applications.workers.workercommon.HealthMonitor
import net.corda.applications.workers.workercommon.StandardWorkerParams
import net.corda.applications.workers.workercommon.getAdditionalConfig
import net.corda.applications.workers.workercommon.getParams
import net.corda.applications.workers.workercommon.setUpHealthMonitor
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.osgi.api.Application
import net.corda.processors.rpc.RPCProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import picocli.CommandLine.Mixin

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

        val params = getParams(args, RPCWorkerParams())
        setUpHealthMonitor(healthMonitor, params.standardWorkerParams)

        val config = getAdditionalConfig(params.standardWorkerParams, smartConfigFactory)
        processor.start(params.standardWorkerParams.instanceId, config)
    }

    override fun shutdown() {
        logger.info("RPC worker stopping.")
        processor.stop()
        healthMonitor.stop()
    }
}

/** Additional parameters for the RPC worker are added here. */
private class RPCWorkerParams {
    @Mixin
    var standardWorkerParams = StandardWorkerParams()
}