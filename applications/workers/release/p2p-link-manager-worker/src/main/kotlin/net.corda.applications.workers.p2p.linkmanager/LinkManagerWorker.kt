package net.corda.applications.workers.p2p.linkmanager

import net.corda.applications.workers.workercommon.ApplicationBanner
import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.WorkerMonitor
import net.corda.applications.workers.workercommon.WorkerHelpers
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.loggerStartupInfo
import net.corda.libs.configuration.SmartConfigFactoryFactory
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.processors.p2p.linkmanager.LinkManagerProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import picocli.CommandLine

@Component
@Suppress("LongParameterList")
class LinkManagerWorker @Activate constructor(
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = LinkManagerProcessor::class)
    private val linkManagerProcessor: LinkManagerProcessor,
    @Reference(service = WorkerMonitor::class)
    private val workerMonitor: WorkerMonitor,
    @Reference(service = ConfigurationValidatorFactory::class)
    private val configurationValidatorFactory: ConfigurationValidatorFactory,
    @Reference(service = PlatformInfoProvider::class)
    val platformInfoProvider: PlatformInfoProvider,
    @Reference(service = ApplicationBanner::class)
    val applicationBanner: ApplicationBanner,
    @Reference(service = SmartConfigFactoryFactory::class)
    val smartConfigFactoryFactory: SmartConfigFactoryFactory,
) : Application {

    private companion object {
        private val logger = contextLogger()
    }

    override fun startup(args: Array<String>) {
        logger.info("P2P Link Manager worker starting.")
        logger.loggerStartupInfo(platformInfoProvider)

        applicationBanner.show("P2P Link Manager Worker", platformInfoProvider)

        val params = WorkerHelpers.getParams(args, LinkManagerWorkerParams())
        if (WorkerHelpers.printHelpOrVersion(params.defaultParams, this::class.java, shutDownService)) return
        WorkerHelpers.setupMonitor(workerMonitor, params.defaultParams, this.javaClass.simpleName)

        val config = WorkerHelpers.getBootstrapConfig(
            smartConfigFactoryFactory,
            params.defaultParams,
            configurationValidatorFactory.createConfigValidator()
        )

        linkManagerProcessor.start(config)
    }

    override fun shutdown() {
        logger.info("P2P Link Manager worker stopping.")
        linkManagerProcessor.stop()
        workerMonitor.stop()
    }
}
/** Additional parameters for the member worker are added here. */
private class LinkManagerWorkerParams {
    @CommandLine.Mixin
    var defaultParams = DefaultWorkerParams()
}