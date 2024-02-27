package net.corda.applications.workers.p2p.gateway

import net.corda.applications.workers.workercommon.ApplicationBanner
import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.Health
import net.corda.applications.workers.workercommon.Metrics
import net.corda.applications.workers.workercommon.WorkerHelpers
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.loggerStartupInfo
import net.corda.libs.configuration.secret.SecretsServiceFactoryResolver
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.registry.LifecycleRegistry
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.processors.p2p.gateway.GatewayProcessor
import net.corda.schema.configuration.BootConfig.BOOT_WORKER_SERVICE
import net.corda.tracing.configureTracing
import net.corda.tracing.shutdownTracing
import net.corda.web.api.WebServer
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import picocli.CommandLine
import picocli.CommandLine.Option

@Component
@Suppress("LongParameterList")
class GatewayWorker @Activate constructor(
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = GatewayProcessor::class)
    private val gatewayProcessor: GatewayProcessor,
    @Reference(service = LifecycleRegistry::class)
    private val lifecycleRegistry: LifecycleRegistry,
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

    override fun startup(args: Array<String>) {
        logger.info("P2P Gateway worker starting.")
        logger.loggerStartupInfo(platformInfoProvider)

        applicationBanner.show("P2P Gateway Worker", platformInfoProvider)

        val params = WorkerHelpers.getParams(args, GatewayWorkerParams())
        if (WorkerHelpers.printHelpOrVersion(params.defaultParams, this::class.java, shutDownService)) return
        Metrics.configure(
            webServer,
            this.javaClass.simpleName,
            params.defaultParams.metricsKeepNames?.toRegex(),
            params.defaultParams.metricsDropLabels?.toRegex()
        )
        Health.configure(webServer, lifecycleRegistry)

        configureTracing("P2P Gateway Worker", params.defaultParams.zipkinTraceUrl, params.defaultParams.traceSamplesPerSecond)

        val config = WorkerHelpers.getBootstrapConfig(
            secretsServiceFactoryResolver,
            params.defaultParams,
            configurationValidatorFactory.createConfigValidator(),
            listOf(WorkerHelpers.createConfigFromParams(BOOT_WORKER_SERVICE, params.workerEndpoints)),
        )
        webServer.start(params.defaultParams.workerServerPort)
        gatewayProcessor.start(config)
    }

    override fun shutdown() {
        logger.info("P2P Gateway worker stopping.")
        gatewayProcessor.stop()
        webServer.stop()
        shutdownTracing()
    }
}

/** Additional parameters for the member worker are added here. */
private class GatewayWorkerParams {
    @CommandLine.Mixin
    var defaultParams = DefaultWorkerParams()

    @Option(
        names = ["--serviceEndpoint"],
        description = ["Internal REST endpoints for Corda workers"],
        required = true,
    )
    val workerEndpoints: Map<String, String> = emptyMap()
}
