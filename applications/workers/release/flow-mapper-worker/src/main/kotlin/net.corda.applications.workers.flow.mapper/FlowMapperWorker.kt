package net.corda.applications.workers.flow.mapper

import com.typesafe.config.Config
import net.corda.applications.workers.workercommon.ApplicationBanner
import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.Health
import net.corda.applications.workers.workercommon.JavaSerialisationFilter
import net.corda.applications.workers.workercommon.Metrics
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.createConfigFromParams
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
import net.corda.processors.flow.mapper.FlowMapperProcessor
import net.corda.schema.configuration.BootConfig.BOOT_WORKER_SERVICE
import net.corda.tracing.configureTracing
import net.corda.tracing.shutdownTracing
import net.corda.web.api.WebServer
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import picocli.CommandLine.Option
import picocli.CommandLine.Mixin

/** The worker for handling mapping of sessions to flows. */
@Suppress("Unused", "LongParameterList")
@Component(service = [Application::class])
class FlowMapperWorker @Activate constructor(
    @Reference(service = FlowMapperProcessor::class)
    private val flowMapperProcessor: FlowMapperProcessor,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = LifecycleRegistry::class)
    private val lifecycleRegistry: LifecycleRegistry,
    @Reference(service = ConfigurationValidatorFactory::class)
    private val configurationValidatorFactory: ConfigurationValidatorFactory,
    @Reference(service = PlatformInfoProvider::class)
    val platformInfoProvider: PlatformInfoProvider,
    @Reference(service = ApplicationBanner::class)
    val applicationBanner: ApplicationBanner,
    @Reference(service = WebServer::class)
    private val webServer: WebServer,
    @Reference(service = SecretsServiceFactoryResolver::class)
    val secretsServiceFactoryResolver: SecretsServiceFactoryResolver,
) : Application {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    /** Parses the arguments, then initialises and starts the [FlowMapperProcessor]. */
    override fun startup(args: Array<String>) {
        logger.info("Flow mapper worker starting.")
        logger.loggerStartupInfo(platformInfoProvider)

        applicationBanner.show("Flow Mapper Worker", platformInfoProvider)

        if (System.getProperty("co.paralleluniverse.fibers.verifyInstrumentation") == true.toString()) {
            logger.info("Quasar's instrumentation verification is enabled")
        }

        JavaSerialisationFilter.install()

        val params = getParams(args, FlowMapperWorkerParams())
        if (printHelpOrVersion(params.defaultParams, FlowMapperWorker::class.java, shutDownService)) return
        Metrics.configure(
            webServer,
            this.javaClass.simpleName,
            params.defaultParams.metricsKeepNames?.toRegex(),
            params.defaultParams.metricsDropLabels?.toRegex()
        )
        Health.configure(webServer, lifecycleRegistry)

        configureTracing("Flow Mapper Worker", params.defaultParams.zipkinTraceUrl, params.defaultParams.traceSamplesPerSecond)
        webServer.start(params.defaultParams.workerServerPort)

        val extraConfigs = mutableListOf<Config>()
        if (params.mediatorReplicasFlowMapperSessionIn != null) {
            extraConfigs.add(
                createConfigFromParams(
                    BOOT_WORKER_SERVICE,
                    mapOf("mediatorReplicas.flowMapperSessionIn" to params.mediatorReplicasFlowMapperSessionIn.toString())
                )
            )
        }

        if (params.mediatorReplicasFlowMapperSessionOut != null) {
            extraConfigs.add(
                createConfigFromParams(
                    BOOT_WORKER_SERVICE,
                    mapOf("mediatorReplicas.flowMapperSessionOut" to params.mediatorReplicasFlowMapperSessionOut.toString())
                )
            )
        }

        val config = getBootstrapConfig(
            secretsServiceFactoryResolver,
            params.defaultParams,
            configurationValidatorFactory.createConfigValidator(),
            extraConfigs
        )

        flowMapperProcessor.start(config)
    }

    override fun shutdown() {
        logger.info("Flow mapper worker stopping.")
        flowMapperProcessor.stop()
        webServer.stop()
        shutdownTracing()
    }
}

/** Additional parameters for the flow mapper worker are added here. */
private class FlowMapperWorkerParams {
    @Mixin
    var defaultParams = DefaultWorkerParams()

    @Option(names = ["--mediator-replicas-flow-session-in"], description = ["Sets the number of mediators that " +
            "consume flow.mapper.session.in messages"])
    var mediatorReplicasFlowMapperSessionIn: Int? = null

    @Option(names = ["--mediator-replicas-flow-session-out"], description = ["Sets the number of mediators that " +
            "consume flow.mapper.session.out messages"])
    var mediatorReplicasFlowMapperSessionOut: Int? = null
}
