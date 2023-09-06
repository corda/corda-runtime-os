package net.corda.applications.workers.member

import net.corda.applications.workers.workercommon.ApplicationBanner
import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getBootstrapConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getParams
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.loggerStartupInfo
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.printHelpOrVersion
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.setupMonitor
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.setupWebserver
import net.corda.applications.workers.workercommon.WorkerMonitor
import net.corda.libs.configuration.secret.SecretsServiceFactoryResolver
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.processors.member.MemberProcessor
import net.corda.tracing.configureTracing
import net.corda.tracing.shutdownTracing
import net.corda.web.api.WebServer
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import picocli.CommandLine.Mixin

/** The worker for handling member services. */
@Suppress("Unused", "LongParameterList")
@Component(service = [Application::class])
class MemberWorker @Activate constructor(
    @Reference(service = MemberProcessor::class)
    private val processor: MemberProcessor,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = WorkerMonitor::class)
    private val workerMonitor: WorkerMonitor,
    @Reference(service = WebServer::class)
    private val webServer: WebServer,
    @Reference(service = ConfigurationValidatorFactory::class)
    private val configurationValidatorFactory: ConfigurationValidatorFactory,
    @Reference(service = PlatformInfoProvider::class)
    val platformInfoProvider: PlatformInfoProvider,
    @Reference(service = ApplicationBanner::class)
    val applicationBanner: ApplicationBanner,
    @Reference(service = SecretsServiceFactoryResolver::class)
    val secretsServiceFactoryResolver: SecretsServiceFactoryResolver,
) : Application {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    /** Parses the arguments, then initialises and starts the [processor]. */
    override fun startup(args: Array<String>) {
        logger.info("Member worker starting.")
        logger.loggerStartupInfo(platformInfoProvider)

        applicationBanner.show("Member Worker", platformInfoProvider)

        val params = getParams(args, MemberWorkerParams())
        webServer.setupWebserver(params.defaultParams)
        if (printHelpOrVersion(params.defaultParams, MemberWorker::class.java, shutDownService)) return
        setupMonitor(workerMonitor, params.defaultParams, this.javaClass.simpleName)

        configureTracing("Member Worker", params.defaultParams.zipkinTraceUrl, params.defaultParams.traceSamplesPerSecond)

        val config = getBootstrapConfig(
            secretsServiceFactoryResolver,
            params.defaultParams,
            configurationValidatorFactory.createConfigValidator())

        processor.start(config)
    }

    override fun shutdown() {
        logger.info("Member worker stopping.")
        processor.stop()
        webServer.stop()
        shutdownTracing()
    }
}

/** Additional parameters for the member worker are added here. */
private class MemberWorkerParams {
    @Mixin
    var defaultParams = DefaultWorkerParams()
}