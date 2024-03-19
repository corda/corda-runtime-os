package net.corda.applications.workers.rest

import net.corda.applications.workers.workercommon.ApplicationBanner
import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.Health
import net.corda.applications.workers.workercommon.JavaSerialisationFilter
import net.corda.applications.workers.workercommon.Metrics
import net.corda.applications.workers.workercommon.WorkerHelpers
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getBootstrapConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getParams
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.loggerStartupInfo
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.printHelpOrVersion
import net.corda.libs.configuration.secret.SecretsServiceFactoryResolver
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.registry.LifecycleRegistry
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.processors.rest.RestProcessor
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.BootConfig.BOOT_REST
import net.corda.schema.configuration.BootConfig.BOOT_REST_TLS_CRT_PATH
import net.corda.schema.configuration.BootConfig.BOOT_REST_TLS_KEYSTORE_FILE_PATH
import net.corda.tracing.configureTracing
import net.corda.tracing.shutdownTracing
import net.corda.web.api.WebServer
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import picocli.CommandLine
import picocli.CommandLine.Mixin

/** The worker for handling REST requests. */
@Suppress("Unused", "LongParameterList")
@Component(service = [Application::class])
class RestWorker @Activate constructor(
    @Reference(service = RestProcessor::class)
    private val processor: RestProcessor,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = LifecycleRegistry::class)
    private val lifecycleRegistry: LifecycleRegistry,
    @Reference(service = WebServer::class)
    private val webServer: WebServer,
    @Reference(service = ConfigurationValidatorFactory::class)
    private val configurationValidatorFactory: ConfigurationValidatorFactory,
    @Reference(service = PlatformInfoProvider::class)
    private val platformInfoProvider: PlatformInfoProvider,
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
        logger.info("REST worker starting.")
        logger.loggerStartupInfo(platformInfoProvider)

        applicationBanner.show("REST Worker", platformInfoProvider)

        JavaSerialisationFilter.install()

        val params = getParams(args, RestWorkerParams())
        params.validate()
        if (printHelpOrVersion(params.defaultParams, RestWorker::class.java, shutDownService)) return
        Metrics.configure(
            webServer,
            this.javaClass.simpleName,
            params.defaultParams.metricsKeepNames?.toRegex(),
            params.defaultParams.metricsDropLabels?.toRegex()
        )
        Health.configure(webServer, lifecycleRegistry)

        logger.info("Trace tags captured from the CLI: ${params.defaultParams.extraTraceTags}")
        configureTracing(
            "REST Worker",
            params.defaultParams.zipkinTraceUrl,
            params.defaultParams.traceSamplesPerSecond,
            params.defaultParams.extraTraceTags
        )

        val config = getBootstrapConfig(
            secretsServiceFactoryResolver,
            params.defaultParams,
            configurationValidatorFactory.createConfigValidator(),
            listOf(WorkerHelpers.createConfigFromParams(BOOT_REST, params.restParams))
        )
        webServer.start(params.defaultParams.workerServerPort)
        processor.start(config)
    }

    override fun shutdown() {
        logger.info("REST worker stopping.")
        processor.stop()
        webServer.stop()
        shutdownTracing()
    }
}

/** Additional parameters for the REST worker are added here. */
private class RestWorkerParams {
    @Mixin
    var defaultParams = DefaultWorkerParams()

    @CommandLine.Option(names = ["-r", "--${BootConfig.BOOT_REST}"], description = ["REST worker specific params."])
    var restParams = emptyMap<String, String>()

    fun validate() {
        if (restParams.containsKey(BOOT_REST_TLS_KEYSTORE_FILE_PATH) &&
            restParams.containsKey(BOOT_REST_TLS_CRT_PATH)
        ) {
            throw IllegalStateException(
                "'$BOOT_REST_TLS_KEYSTORE_FILE_PATH' and '$BOOT_REST_TLS_CRT_PATH' " +
                    "are mutually exclusive for TLS certificate provisions."
            )
        }
    }
}
