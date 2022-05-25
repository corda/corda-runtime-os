package net.corda.applications.workers.rpc

import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.HealthMonitor
import net.corda.applications.workers.workercommon.JavaSerialisationFilter
import net.corda.applications.workers.workercommon.PathAndConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getBootstrapConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getParams
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.printHelpOrVersion
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.setUpHealthMonitor
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.processors.rpc.RPCProcessor
import net.corda.schema.configuration.BootConfig.BOOT_RPC
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option

/** The worker for handling RPC requests. */
@Suppress("Unused")
@Component(service = [Application::class])
class RPCWorker @Activate constructor(
    @Reference(service = RPCProcessor::class)
    private val processor: RPCProcessor,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = HealthMonitor::class)
    private val healthMonitor: HealthMonitor,
    @Reference(service = ConfigurationValidatorFactory::class)
    private val configurationValidatorFactory: ConfigurationValidatorFactory
) : Application {

    private companion object {
        private val logger = contextLogger()
    }

    /** Parses the arguments, then initialises and starts the [processor]. */
    override fun startup(args: Array<String>) {
        logger.info("RPC worker starting.")
        JavaSerialisationFilter.install()

        val params = getParams(args, RPCWorkerParams())
        if (printHelpOrVersion(params.defaultParams, RPCWorker::class.java, shutDownService)) return
        setUpHealthMonitor(healthMonitor, params.defaultParams)

        val rpcConfig = PathAndConfig(BOOT_RPC, params.rpcParams)
        val config = getBootstrapConfig(params.defaultParams, configurationValidatorFactory.createConfigValidator(), listOf(rpcConfig))

        processor.start(config)
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
    var defaultParams = DefaultWorkerParams()

    @Option(names = ["-r", "--rpcParams"], description = ["RPC parameters for the worker."])
    var rpcParams = emptyMap<String, String>()
}