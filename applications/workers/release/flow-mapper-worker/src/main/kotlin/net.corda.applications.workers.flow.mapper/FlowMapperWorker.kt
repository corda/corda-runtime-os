package net.corda.applications.workers.flow.mapper

import net.corda.applications.workers.workercommon.ApplicationBanner
import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.JavaSerialisationFilter
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getBootstrapConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getParams
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.loggerStartupInfo
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.printHelpOrVersion
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.setupMonitor
import net.corda.applications.workers.workercommon.WorkerMonitor
import net.corda.libs.configuration.secret.SecretsServiceFactoryResolver
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.processors.flow.mapper.FlowMapperProcessor
import net.corda.tracing.configureTracing
import net.corda.tracing.shutdownTracing
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import picocli.CommandLine.Mixin

/** The worker for handling mapping of sessions to flows. */
@Suppress("Unused", "LongParameterList")
@Component(service = [Application::class])
class FlowMapperWorker @Activate constructor(
    @Reference(service = FlowMapperProcessor::class)
    private val flowMapperProcessor: FlowMapperProcessor,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = WorkerMonitor::class)
    private val workerMonitor: WorkerMonitor,
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
        setupMonitor(workerMonitor, params.defaultParams, this.javaClass.simpleName)

        configureTracing("Flow Mapper Worker", params.defaultParams.zipkinTraceUrl, params.defaultParams.traceSamplesPerSecond)

        val config = getBootstrapConfig(
            secretsServiceFactoryResolver,
            params.defaultParams,
            configurationValidatorFactory.createConfigValidator())

        flowMapperProcessor.start(config)
    }

    override fun shutdown() {
        logger.info("Flow mapper worker stopping.")
        flowMapperProcessor.stop()
        workerMonitor.stop()
        shutdownTracing()
    }
}

/** Additional parameters for the flow mapper worker are added here. */
private class FlowMapperWorkerParams {
    @Mixin
    var defaultParams = DefaultWorkerParams()
}