package net.corda.applications.workers.flow

import com.typesafe.config.Config
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
import net.corda.processors.flow.FlowProcessor
import net.corda.schema.configuration.BootConfig.BOOT_WORKER_SERVICE
import net.corda.schema.configuration.BootConfig.WORKER_MEDIATOR_REPLICAS_FLOW_SESSION
import net.corda.tracing.configureTracing
import net.corda.tracing.shutdownTracing
import net.corda.web.api.WebServer
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option

/** The worker for handling flows. */
@Suppress("Unused", "LongParameterList")
@Component(service = [Application::class])
class FlowWorker @Activate constructor(
    @Reference(service = FlowProcessor::class)
    private val flowProcessor: FlowProcessor,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
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

    /** Parses the arguments, then initialises and starts the [flowProcessor]. */
    override fun startup(args: Array<String>) {
        logger.info("Flow worker starting.")
        logger.loggerStartupInfo(platformInfoProvider)

        applicationBanner.show("Flow Worker", platformInfoProvider)

        if (System.getProperty("co.paralleluniverse.fibers.verifyInstrumentation") == true.toString()) {
            logger.info("Quasar's instrumentation verification is enabled")
        }

        JavaSerialisationFilter.install()

        val params = getParams(args, FlowWorkerParams())

        if (printHelpOrVersion(params.defaultParams, FlowWorker::class.java, shutDownService)) return
        Metrics.configure(
            webServer,
            this.javaClass.simpleName,
            params.defaultParams.metricsKeepNames?.toRegex(),
            params.defaultParams.metricsDropLabels?.toRegex()
        )
        Health.configure(webServer, lifecycleRegistry)

        configureTracing("Flow Worker", params.defaultParams.zipkinTraceUrl, params.defaultParams.traceSamplesPerSecond)
        webServer.start(params.defaultParams.workerServerPort)

        val extraConfigs = mutableListOf(
            WorkerHelpers.createConfigFromParams(BOOT_WORKER_SERVICE, params.workerEndpoints)
        )

        if (params.mediatorReplicasFlowSession != null) {
            extraConfigs.add(
                WorkerHelpers.createConfigFromParams(
                    BOOT_WORKER_SERVICE,
                    mapOf("mediatorReplicas.flowSession" to params.mediatorReplicasFlowSession.toString())
                )
            )
        }

        val config = getBootstrapConfig(
            secretsServiceFactoryResolver,
            params.defaultParams,
            configurationValidatorFactory.createConfigValidator(),
            extraConfigs
        )

        flowProcessor.start(config)
    }

    override fun shutdown() {
        logger.info("Flow worker stopping.")
        flowProcessor.stop()
        webServer.stop()
        shutdownTracing()
    }
}

/** Additional parameters for the flow worker are added here. */
private class FlowWorkerParams {
    @Mixin
    var defaultParams = DefaultWorkerParams()

    @Option(names = ["--serviceEndpoint"], description = ["Internal REST endpoints for Corda workers"], required = true)
    val workerEndpoints: Map<String, String> = emptyMap()

    @Option(names = ["--mediator-replicas-flow-session"], description = ["Sets the number of mediators that consume " +
            "flow.session messages"])
    var mediatorReplicasFlowSession: Int? = null
}
