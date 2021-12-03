package net.corda.applications.workers.crypto

import net.corda.applications.workers.workercommon.HealthMonitor
import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.getAdditionalConfig
import net.corda.applications.workers.workercommon.getParams
import net.corda.applications.workers.workercommon.setUpHealthMonitor
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.osgi.api.Application
import net.corda.processors.crypto.CryptoProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import picocli.CommandLine.Mixin

/** The worker for interacting with the key material. */
@Suppress("Unused")
@Component(service = [Application::class])
class CryptoWorker @Activate constructor(
    @Reference(service = SmartConfigFactory::class)
    private val smartConfigFactory: SmartConfigFactory,
    @Reference(service = HealthMonitor::class)
    private val healthMonitor: HealthMonitor,
    @Reference(service = CryptoProcessor::class)
    private val processor: CryptoProcessor
) : Application {

    private companion object {
        private val logger = contextLogger()
    }

    /** Parses the arguments, then initialises and starts the [processor]. */
    override fun startup(args: Array<String>) {
        logger.info("Crypto worker starting.")

        val params = getParams(args, CryptoWorkerParams())
        setUpHealthMonitor(healthMonitor, params.defaultParams)

        val config = getAdditionalConfig(params.defaultParams, smartConfigFactory)
        processor.start(params.defaultParams.instanceId, config)
    }

    override fun shutdown() {
        logger.info("Crypto worker stopping.")
        processor.stop()
        healthMonitor.stop()
    }
}

/** Additional parameters for the crypto worker are added here. */
private class CryptoWorkerParams {
    @Mixin
    var defaultParams = DefaultWorkerParams()
}