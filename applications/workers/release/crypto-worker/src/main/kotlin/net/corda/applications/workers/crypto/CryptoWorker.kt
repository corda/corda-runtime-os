package net.corda.applications.workers.crypto

import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.HealthMonitor
import net.corda.applications.workers.workercommon.JavaSerialisationFilter
import net.corda.applications.workers.workercommon.PathAndConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getBootstrapConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getParams
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.printHelpOrVersion
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.setUpHealthMonitor
import net.corda.crypto.config.impl.createCryptoBootstrapParamsMap
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.processors.crypto.CryptoProcessor
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.BootConfig.BOOT_CRYPTO
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import picocli.CommandLine
import picocli.CommandLine.Mixin

/** The worker for interacting with the key material. */
@Suppress("Unused")
@Component(service = [Application::class])
class CryptoWorker @Activate constructor(
    @Reference(service = CryptoProcessor::class)
    private val processor: CryptoProcessor,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = HealthMonitor::class)
    private val healthMonitor: HealthMonitor,
    @Reference(service = ConfigurationValidatorFactory::class)
    private val configurationValidatorFactory: ConfigurationValidatorFactory
) : Application {

    private companion object {
        private val logger = contextLogger()
    }

    override fun startup(args: Array<String>) {
        logger.info("Crypto worker starting.")
        JavaSerialisationFilter.install()
        val params = getParams(args, CryptoWorkerParams())
        if (printHelpOrVersion(params.defaultParams, CryptoWorker::class.java, shutDownService)) {
            return
        }
        if (params.hsmId.isBlank()) {
            println("Please specify which HSM the worker must handle, like --hsm-id SOFT")
            return
        }
        setUpHealthMonitor(healthMonitor, params.defaultParams)
        processor.start(
            buildBoostrapConfig(params, configurationValidatorFactory)
        )
    }

    override fun shutdown() {
        logger.info("Crypto worker stopping.")
        processor.stop()
        healthMonitor.stop()
    }

    private fun buildBoostrapConfig(
        params: CryptoWorkerParams,
        configurationValidatorFactory: ConfigurationValidatorFactory
    ): SmartConfig = getBootstrapConfig(
        params.defaultParams,
        configurationValidatorFactory.createConfigValidator(),
        listOf(
            PathAndConfig(BootConfig.BOOT_DB_PARAMS, params.databaseParams),
            PathAndConfig(BOOT_CRYPTO, createCryptoBootstrapParamsMap(params.hsmId))
        )
    )
}

class CryptoWorkerParams {
    @Mixin
    var defaultParams = DefaultWorkerParams()

    @CommandLine.Option(names = ["-d", "--databaseParams"], description = ["Database parameters for the worker."])
    var databaseParams = emptyMap<String, String>()

    @CommandLine.Option(names = ["--hsm-id"], description = ["HSM ID which is handled by this worker instance."])
    var hsmId = ""
}