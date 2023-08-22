package net.corda.applications.workers.db

import net.corda.applications.workers.workercommon.ApplicationBanner
import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.JavaSerialisationFilter
import net.corda.applications.workers.workercommon.PathAndConfig
import net.corda.applications.workers.workercommon.WorkerHelpers
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
import net.corda.processors.persistence.PersistenceProcessor
import net.corda.schema.configuration.BootConfig
import net.corda.tracing.configureTracing
import net.corda.tracing.shutdownTracing
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import picocli.CommandLine
import picocli.CommandLine.Mixin

/** The worker for uniqueness checks. */
@Suppress("Unused", "LongParameterList")
@Component(service = [Application::class])
class PersistenceWorker @Activate constructor(
    @Reference(service = PersistenceProcessor::class)
    private val persistenceProcessor: PersistenceProcessor,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = WorkerMonitor::class)
    private val workerMonitor: WorkerMonitor,
    @Reference(service = PlatformInfoProvider::class)
    val platformInfoProvider: PlatformInfoProvider,
    @Reference(service = ApplicationBanner::class)
    val applicationBanner: ApplicationBanner,
    @Reference(service = ConfigurationValidatorFactory::class)
    private val configurationValidatorFactory: ConfigurationValidatorFactory,
    @Reference(service = SecretsServiceFactoryResolver::class)
    val secretsServiceFactoryResolver: SecretsServiceFactoryResolver,
) : Application {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    /** Parses the arguments, then initialises and starts the [processor]. */
    override fun startup(args: Array<String>) {
        logger.info("Persistence worker starting.")
        logger.loggerStartupInfo(platformInfoProvider)

        applicationBanner.show("Persistence Worker", platformInfoProvider)

        JavaSerialisationFilter.install()

        val params = getParams(args, PersistenceWorkerParams())
        if (printHelpOrVersion(params.defaultParams, PersistenceWorker::class.java, shutDownService)) return
        setupMonitor(workerMonitor, params.defaultParams, this.javaClass.simpleName)

        configureTracing("Persistence Worker", params.defaultParams.zipkinTraceUrl, params.defaultParams.traceSamplesPerSecond)

        val databaseConfig = PathAndConfig(BootConfig.BOOT_DB, params.databaseParams)
        val config = WorkerHelpers.getBootstrapConfig(
            secretsServiceFactoryResolver,
            params.defaultParams,
            configurationValidatorFactory.createConfigValidator(),
            listOf(databaseConfig)
        )

        persistenceProcessor.start(config)
    }

    override fun shutdown() {
        logger.info("Persistence worker stopping.")
        workerMonitor.stop()
        shutdownTracing()
    }
}

/** Additional parameters for the Persistence worker are added here. */
private class PersistenceWorkerParams {
    @Mixin
    var defaultParams = DefaultWorkerParams()

    @CommandLine.Option(names = ["-d", "--${BootConfig.BOOT_DB}"], description = ["Database parameters for the worker."])
    var databaseParams = emptyMap<String, String>()
}