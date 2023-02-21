package net.corda.applications.workers.rest

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
import net.corda.processors.rest.RestProcessor
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import picocli.CommandLine.Mixin

/** The worker for handling REST requests. */
@Suppress("Unused", "LongParameterList")
@Component(service = [Application::class])
class RestWorker @Activate constructor(
    @Reference(service = RestProcessor::class)
    private val processor: RestProcessor,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = WorkerMonitor::class)
    private val workerMonitor: WorkerMonitor,
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
        if (printHelpOrVersion(params.defaultParams, RestWorker::class.java, shutDownService)) return
        setupMonitor(workerMonitor, params.defaultParams, this.javaClass.simpleName)

        val config =
            getBootstrapConfig(
                secretsServiceFactoryResolver,
                params.defaultParams,
                configurationValidatorFactory.createConfigValidator(), listOf())

        processor.start(config)
    }

    override fun shutdown() {
        logger.info("REST worker stopping.")
        processor.stop()
        workerMonitor.stop()
    }
}

/** Additional parameters for the REST worker are added here. */
private class RestWorkerParams {
    @Mixin
    var defaultParams = DefaultWorkerParams()
}