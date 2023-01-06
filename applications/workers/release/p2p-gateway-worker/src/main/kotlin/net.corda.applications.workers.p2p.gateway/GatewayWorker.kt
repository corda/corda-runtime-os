package net.corda.applications.workers.p2p.gateway

import net.corda.applications.workers.workercommon.ApplicationBanner
import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.WorkerMonitor
import net.corda.applications.workers.workercommon.WorkerHelpers
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.loggerStartupInfo
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.processors.p2p.gateway.GatewayProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import picocli.CommandLine

@Component
@Suppress("LongParameterList")
class GatewayWorker @Activate constructor(
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = GatewayProcessor::class)
    private val gatewayProcessor: GatewayProcessor,
    @Reference(service = WorkerMonitor::class)
    private val workerMonitor: WorkerMonitor,
    @Reference(service = ConfigurationValidatorFactory::class)
    private val configurationValidatorFactory: ConfigurationValidatorFactory,
    @Reference(service = PlatformInfoProvider::class)
    val platformInfoProvider: PlatformInfoProvider,
    @Reference(service = ApplicationBanner::class)
    val applicationBanner: ApplicationBanner,
) : Application {

    private companion object {
        private val logger = contextLogger()
    }

    override fun startup(args: Array<String>) {
        logger.info("P2P Gateway worker starting.")
        logger.loggerStartupInfo(platformInfoProvider)

        applicationBanner.show("P2P Gateway Worker", platformInfoProvider)

        val params = WorkerHelpers.getParams(args, GatewayWorkerParams())
        if (WorkerHelpers.printHelpOrVersion(params.defaultParams, this::class.java, shutDownService)) return
        WorkerHelpers.setupMonitor(workerMonitor, params.defaultParams, this.javaClass.simpleName)

        val config = WorkerHelpers.getBootstrapConfig(
            params.defaultParams,
            configurationValidatorFactory.createConfigValidator()
        )

        gatewayProcessor.start(config, !params.withoutStubs)
    }

    override fun shutdown() {
        logger.info("P2P Gateway worker stopping.")
        gatewayProcessor.stop()
        workerMonitor.stop()
    }
}
/** Additional parameters for the member worker are added here. */
private class GatewayWorkerParams {
    @CommandLine.Mixin
    var defaultParams = DefaultWorkerParams()

    //This is used to test the gateway without Crypto component. It will be removed in CORE-5782.
    @CommandLine.Option(names = ["--without-stubs"])
    var withoutStubs = false
}
