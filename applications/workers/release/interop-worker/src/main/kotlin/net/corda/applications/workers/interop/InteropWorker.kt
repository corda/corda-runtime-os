package net.corda.applications.workers.interop

import net.corda.applications.workers.workercommon.ApplicationBanner
import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.JavaSerialisationFilter
import net.corda.applications.workers.workercommon.WorkerHelpers
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.loggerStartupInfo
import net.corda.applications.workers.workercommon.WorkerMonitor
import net.corda.libs.configuration.secret.SecretsServiceFactoryResolver
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.processors.interop.InteropProcessor
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import picocli.CommandLine.Mixin

// Based on FlowWorker without verificationProcessor
/** The worker for handling flows. */
@Suppress("Unused", "LongParameterList")
@Component(service = [Application::class])
class InteropWorker @Activate constructor(
    @Reference(service = InteropProcessor::class)
    private val interopProcessor: InteropProcessor,
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

    /** Parses the arguments, then initialises and starts the [flowProcessor] and [verificationProcessor]. */
    override fun startup(args: Array<String>) {
        logger.info("Flow worker starting.")
        logger.loggerStartupInfo(platformInfoProvider)

        applicationBanner.show("InterOp Worker", platformInfoProvider)

        if (System.getProperty("co.paralleluniverse.fibers.verifyInstrumentation") == true.toString()) {
            logger.info("Quasar's instrumentation verification is enabled")
        }

        JavaSerialisationFilter.install()

        val params = WorkerHelpers.getParams(args, InteropWorkerParams())
        if (WorkerHelpers.printHelpOrVersion(params.defaultParams, InteropWorker::class.java, shutDownService)) return
        WorkerHelpers.setupMonitor(workerMonitor, params.defaultParams, this.javaClass.simpleName)

        val config = WorkerHelpers.getBootstrapConfig(
            secretsServiceFactoryResolver,
            params.defaultParams,
            configurationValidatorFactory.createConfigValidator())

        interopProcessor.start(config)
    }

    override fun shutdown() {
        logger.info("Flow worker stopping.")
        interopProcessor.stop()
        workerMonitor.stop()
    }
}

/** Additional parameters for the flow worker are added here. */
private class InteropWorkerParams {
    @Mixin
    var defaultParams = DefaultWorkerParams()
}