package net.corda.applications.workers.combined

import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.HealthMonitor
import net.corda.applications.workers.workercommon.PathAndConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getBootstrapConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getParams
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.printHelpOrVersion
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.setUpHealthMonitor
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.processors.crypto.CryptoProcessor
import net.corda.processors.db.DBProcessor
import net.corda.processors.flow.FlowProcessor
import net.corda.processors.rpc.RPCProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option

/** A worker that starts all processors. */
@Suppress("Unused", "LongParameterList")
@Component(service = [Application::class])
class CombinedWorker @Activate constructor(
    @Reference(service = CryptoProcessor::class)
    private val cryptoProcessor: CryptoProcessor,
    @Reference(service = DBProcessor::class)
    private val dbProcessor: DBProcessor,
    @Reference(service = FlowProcessor::class)
    private val flowProcessor: FlowProcessor,
    @Reference(service = RPCProcessor::class)
    private val rpcProcessor: RPCProcessor,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = SmartConfigFactory::class)
    private val smartConfigFactory: SmartConfigFactory,
    @Reference(service = HealthMonitor::class)
    private val healthMonitor: HealthMonitor
) : Application {

    private companion object {
        private val logger = contextLogger()
        private const val DB_CONFIG_PATH = "database"
        private const val RPC_CONFIG_PATH = "rpc"
    }

    /** Parses the arguments, then initialises and starts the processors. */
    override fun startup(args: Array<String>) {
        logger.info("Combined worker starting.")

        val params = getParams(args, CombinedWorkerParams())
        if (printHelpOrVersion(params.defaultParams, CombinedWorker::class.java, shutDownService)) return
        setUpHealthMonitor(healthMonitor, params.defaultParams)

        val databaseConfig = PathAndConfig(DB_CONFIG_PATH, params.databaseParams)
        val rpcConfig = PathAndConfig(RPC_CONFIG_PATH, params.rpcParams)
        val config = getBootstrapConfig(smartConfigFactory, params.defaultParams, listOf(databaseConfig, rpcConfig))

        cryptoProcessor.start(config)
        dbProcessor.start(config)
        flowProcessor.start(config)
        rpcProcessor.start(config)
    }

    override fun shutdown() {
        logger.info("Combined worker stopping.")

        cryptoProcessor.stop()
        dbProcessor.stop()
        flowProcessor.stop()
        rpcProcessor.stop()

        healthMonitor.stop()
    }
}

/** Additional parameters for the combined worker are added here. */
private class CombinedWorkerParams {
    @Mixin
    var defaultParams = DefaultWorkerParams()

    @Option(names = ["-d", "--databaseParams"], description = ["Database parameters for the worker."])
    var databaseParams = emptyMap<String, String>()

    @Option(names = ["-r", "--rpcParams"], description = ["RPC parameters for the worker."])
    var rpcParams = emptyMap<String, String>()
}