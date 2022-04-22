package net.corda.applications.workers.db

import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.HealthMonitor
import net.corda.applications.workers.workercommon.PathAndConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getBootstrapConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getParams
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.printHelpOrVersion
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.setUpHealthMonitor
import net.corda.applications.workers.workercommon.JavaSerialisationFilter
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.processors.db.DBProcessor
import net.corda.schema.configuration.ConfigDefaults
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.ConfigKeys.DB_CONFIG
import net.corda.schema.configuration.ConfigKeys.RECONCILIATION_CONFIG
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option

/** The worker for interacting with the database. */
@Suppress("Unused")
@Component(service = [Application::class])
class DBWorker @Activate constructor(
    @Reference(service = DBProcessor::class)
    private val processor: DBProcessor,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = HealthMonitor::class)
    private val healthMonitor: HealthMonitor
) : Application {

    private companion object {
        private val logger = contextLogger()
    }

    /** Parses the arguments, then initialises and starts the [processor]. */
    override fun startup(args: Array<String>) {
        logger.info("DB worker starting.")
        JavaSerialisationFilter.install()

        val params = getParams(args, DBWorkerParams())
        if (printHelpOrVersion(params.defaultParams, DBWorker::class.java, shutDownService)) return
        setUpHealthMonitor(healthMonitor, params.defaultParams)

        val databaseConfig = PathAndConfig(DB_CONFIG, params.databaseParams)
        val reconciliationTaskConfig = getReconciliationTaskConfigWithDefaults(params.reconciliationTaskParams)
        val config = getBootstrapConfig(params.defaultParams, listOf(databaseConfig, reconciliationTaskConfig))

        processor.start(config)
    }

    private fun getReconciliationTaskConfigWithDefaults(reconciliationTaskParams: Map<String, String>): PathAndConfig {
        val fallback: MutableMap<String, String> = mutableMapOf(
            ConfigKeys.RECONCILIATION_PERMISSION_SUMMARY_INTERVAL_MS to
                    ConfigDefaults.RECONCILIATION_PERMISSION_SUMMARY_INTERVAL_MS.toString(),
            ConfigKeys.RECONCILIATION_CPK_WRITE_INTERVAL_MS to
                    ConfigDefaults.RECONCILIATION_CPK_WRITE_INTERVAL_MS.toString()
        )
        fallback.putAll(reconciliationTaskParams)
        return PathAndConfig(RECONCILIATION_CONFIG, fallback)
    }

    override fun shutdown() {
        logger.info("DB worker stopping.")
        processor.stop()
        healthMonitor.stop()
    }
}

/** Additional parameters for the DB worker are added here. */
private class DBWorkerParams {
    @Mixin
    var defaultParams = DefaultWorkerParams()

    @Option(names = ["-d", "--databaseParams"], description = ["Database parameters for the worker."])
    var databaseParams = emptyMap<String, String>()

    @Option(names = ["-r", "--reconciliationTaskParams"], description = ["Parameters for reconciliation tasks run on the database worker."])
    var reconciliationTaskParams: Map<String, String> = emptyMap()
}