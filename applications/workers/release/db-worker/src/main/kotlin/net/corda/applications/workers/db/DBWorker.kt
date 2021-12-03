package net.corda.applications.workers.db

import net.corda.applications.workers.workercommon.HealthMonitor
import net.corda.applications.workers.workercommon.StandardWorkerParams
import net.corda.applications.workers.workercommon.getAdditionalConfig
import net.corda.applications.workers.workercommon.getParams
import net.corda.applications.workers.workercommon.setUpHealthMonitor
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.osgi.api.Application
import net.corda.processors.db.DBProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import picocli.CommandLine.Mixin

/** The worker for interacting with the database. */
@Suppress("Unused")
@Component(service = [Application::class])
class DBWorker @Activate constructor(
    @Reference(service = SmartConfigFactory::class)
    private val smartConfigFactory: SmartConfigFactory,
    @Reference(service = HealthMonitor::class)
    private val healthMonitor: HealthMonitor,
    @Reference(service = DBProcessor::class)
    private val processor: DBProcessor
) : Application {

    private companion object {
        private val logger = contextLogger()
    }

    /** Parses the arguments, then initialises and starts the [DBProcessor]. */
    override fun startup(args: Array<String>) {
        logger.info("DB worker starting.")

        val params = getParams(args, RPCWorkerParams())
        setUpHealthMonitor(healthMonitor, params.standardWorkerParams)

        val config = getAdditionalConfig(params.standardWorkerParams, smartConfigFactory)
        processor.start(params.standardWorkerParams.instanceId, config)
    }

    override fun shutdown() {
        logger.info("DB worker stopping.")
        processor.stop()
        healthMonitor.stop()
    }
}

/** Additional parameters for the DB worker are added here. */
private class RPCWorkerParams {
    @Mixin
    var standardWorkerParams = StandardWorkerParams()
}