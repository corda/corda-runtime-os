package net.corda.applications.workers.token.selection

import net.corda.applications.workers.workercommon.ApplicationBanner
import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.Health
import net.corda.applications.workers.workercommon.JavaSerialisationFilter
import net.corda.applications.workers.workercommon.Metrics
import net.corda.applications.workers.workercommon.WorkerHelpers
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getParams
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.loggerStartupInfo
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.printHelpOrVersion
import net.corda.libs.configuration.secret.SecretsServiceFactoryResolver
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.registry.LifecycleRegistry
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.processors.token.cache.TokenCacheProcessor
import net.corda.schema.configuration.BootConfig
import net.corda.tracing.configureTracing
import net.corda.tracing.shutdownTracing
import net.corda.web.api.WebServer
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import picocli.CommandLine
import picocli.CommandLine.Mixin

@Suppress("Unused", "LongParameterList")
@Component(service = [Application::class])
class TokenSelectionWorker @Activate constructor(
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = LifecycleRegistry::class)
    private val lifecycleRegistry: LifecycleRegistry,
    @Reference(service = WebServer::class)
    private val webServer: WebServer,
    @Reference(service = PlatformInfoProvider::class)
    val platformInfoProvider: PlatformInfoProvider,
    @Reference(service = ApplicationBanner::class)
    val applicationBanner: ApplicationBanner,
    @Reference(service = ConfigurationValidatorFactory::class)
    private val configurationValidatorFactory: ConfigurationValidatorFactory,
    @Reference(service = SecretsServiceFactoryResolver::class)
    val secretsServiceFactoryResolver: SecretsServiceFactoryResolver,
    @Reference(service = TokenCacheProcessor::class)
    private val tokenCacheProcessor: TokenCacheProcessor,
) : Application {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    /** Parses the arguments, then initialises and starts the [processor]. */
    override fun startup(args: Array<String>) {
        logger.info("Token selection worker starting.")
        logger.loggerStartupInfo(platformInfoProvider)

        applicationBanner.show("Token Selection Worker", platformInfoProvider)

        JavaSerialisationFilter.install()

        val params = getParams(args, TokenSelectionWorkerParams())
        if (printHelpOrVersion(params.defaultParams, TokenSelectionWorker::class.java, shutDownService)) return
        Metrics.configure(webServer, this.javaClass.simpleName)
        Health.configure(webServer, lifecycleRegistry)

        configureTracing("Token selection Worker", params.defaultParams.zipkinTraceUrl, params.defaultParams.traceSamplesPerSecond)

        val config = WorkerHelpers.getBootstrapConfig(
            secretsServiceFactoryResolver,
            params.defaultParams,
            configurationValidatorFactory.createConfigValidator(),
            listOf(WorkerHelpers.createConfigFromParams(BootConfig.BOOT_DB, params.databaseParams))
        )

        webServer.start(params.defaultParams.workerServerPort)
        tokenCacheProcessor.start(config)
    }

    override fun shutdown() {
        logger.info("Token selection worker stopping.")
        webServer.stop()
        tokenCacheProcessor.stop()
        shutdownTracing()
    }
}

/** Additional parameters for the token selection worker are added here. */
private class TokenSelectionWorkerParams {
    @Mixin
    var defaultParams = DefaultWorkerParams()

    @CommandLine.Option(names = ["-d", "--${BootConfig.BOOT_DB}"], description = ["Database parameters for the worker."])
    var databaseParams = emptyMap<String, String>()
}