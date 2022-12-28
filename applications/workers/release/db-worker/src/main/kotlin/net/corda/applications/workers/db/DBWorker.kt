package net.corda.applications.workers.db

import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.JavaSerialisationFilter
import net.corda.applications.workers.workercommon.PathAndConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getBootstrapConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getParams
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.loggerStartupInfo
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.printHelpOrVersion
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.setupMonitor
import net.corda.applications.workers.workercommon.WorkerMonitor
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.processors.db.DBProcessor
import net.corda.schema.configuration.BootConfig.BOOT_DB_PARAMS
import net.corda.processors.uniqueness.UniquenessProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option

/** The worker for interacting with the database. */
@Suppress("Unused", "LongParameterList")
@Component(service = [Application::class])
class DBWorker @Activate constructor(
    @Reference(service = DBProcessor::class)
    private val processor: DBProcessor,
    @Reference(service = UniquenessProcessor::class)
    private val uniquenessProcessor: UniquenessProcessor,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = WorkerMonitor::class)
    private val workerMonitor: WorkerMonitor,
    @Reference(service = ConfigurationValidatorFactory::class)
    private val configurationValidatorFactory: ConfigurationValidatorFactory,
    @Reference(service = PlatformInfoProvider::class)
    val platformInfoProvider: PlatformInfoProvider
) : Application {

    private companion object {
        private val logger = contextLogger()
    }

    /** Parses the arguments, then initialises and starts the [processor]. */
    override fun startup(args: Array<String>) {
        logger.info("DB worker starting.")
        logger.loggerStartupInfo(platformInfoProvider)

        JavaSerialisationFilter.install()

        val params = getParams(args, DBWorkerParams())
        if (printHelpOrVersion(params.defaultParams, DBWorker::class.java, shutDownService)) return
        setupMonitor(workerMonitor, params.defaultParams, this.javaClass.simpleName)

        val databaseConfig = PathAndConfig(BOOT_DB_PARAMS, params.databaseParams)
        val config = getBootstrapConfig(
            params.defaultParams,
            configurationValidatorFactory.createConfigValidator(),
            listOf(databaseConfig)
        )

        processor.start(config)
        uniquenessProcessor.start()
    }

    override fun shutdown() {
        logger.info("DB worker stopping.")
        processor.stop()
        workerMonitor.stop()
    }
}

/** Additional parameters for the DB worker are added here. */
private class DBWorkerParams {
    @Mixin
    var defaultParams = DefaultWorkerParams()

    @Option(names = ["-d", "--database-params"], description = ["Database parameters for the worker."])
    var databaseParams = emptyMap<String, String>()
}
