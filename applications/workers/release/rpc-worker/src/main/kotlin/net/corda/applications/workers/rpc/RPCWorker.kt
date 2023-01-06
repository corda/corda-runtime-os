package net.corda.applications.workers.rpc

import net.corda.applications.workers.workercommon.ApplicationBanner
import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.WorkerMonitor
import net.corda.applications.workers.workercommon.JavaSerialisationFilter
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getBootstrapConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getParams
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.loggerStartupInfo
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.printHelpOrVersion
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.setupMonitor
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.processors.rpc.RPCProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import picocli.CommandLine.Mixin

/** The worker for handling RPC requests. */
@Suppress("Unused", "LongParameterList")
@Component(service = [Application::class])
class RPCWorker @Activate constructor(
    @Reference(service = RPCProcessor::class)
    private val processor: RPCProcessor,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = WorkerMonitor::class)
    private val workerMonitor: WorkerMonitor,
    @Reference(service = ConfigurationValidatorFactory::class)
    private val configurationValidatorFactory: ConfigurationValidatorFactory,
    @Reference(service = PlatformInfoProvider::class)
    private val platformInfoProvider: PlatformInfoProvider,
    @Reference(service = ApplicationBanner::class)
    val applicationBanner: ApplicationBanner,
) : Application {

    private companion object {
        private val logger = contextLogger()
    }

    /** Parses the arguments, then initialises and starts the [processor]. */
    override fun startup(args: Array<String>) {
        logger.info("RPC worker starting.")
        logger.loggerStartupInfo(platformInfoProvider)

        applicationBanner.show("RPC Worker", platformInfoProvider)

        JavaSerialisationFilter.install()

        val params = getParams(args, RPCWorkerParams())
        if (printHelpOrVersion(params.defaultParams, RPCWorker::class.java, shutDownService)) return
        setupMonitor(workerMonitor, params.defaultParams, this.javaClass.simpleName)

        val config =
            getBootstrapConfig(params.defaultParams, configurationValidatorFactory.createConfigValidator(), listOf())

        processor.start(config)
    }

    override fun shutdown() {
        logger.info("RPC worker stopping.")
        processor.stop()
        workerMonitor.stop()
    }
}

/** Additional parameters for the RPC worker are added here. */
private class RPCWorkerParams {
    @Mixin
    var defaultParams = DefaultWorkerParams()
}