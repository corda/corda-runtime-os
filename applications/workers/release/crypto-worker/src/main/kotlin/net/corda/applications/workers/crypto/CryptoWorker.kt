package net.corda.applications.workers.crypto

import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.HealthMonitor
import net.corda.applications.workers.workercommon.JavaSerialisationFilter
import net.corda.applications.workers.workercommon.PathAndConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getBootstrapConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getParams
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.printHelpOrVersion
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.setUpHealthMonitor
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.applications.workers.workercommon.JavaSerialisationFilter
import net.corda.applications.workers.workercommon.PathAndConfig
import net.corda.crypto.core.aes.KeyCredentials
import net.corda.crypto.impl.config.addDefaultCryptoConfig
import net.corda.libs.configuration.SmartConfig
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

    /** Parses the arguments, then initialises and starts the [processor] and [dependenciesProcessor]. */
    override fun startup(args: Array<String>) {
        logger.info("Crypto worker starting.")
        JavaSerialisationFilter.install()

        val params = getParams(args, CryptoWorkerParams())
        if (printHelpOrVersion(params.defaultParams, CryptoWorker::class.java, shutDownService)) return
        setUpHealthMonitor(healthMonitor, params.defaultParams)

        val config = buildBoostrapConfig(params)

        processor.start(config)
    }

    override fun shutdown() {
        logger.info("Crypto worker stopping.")
        processor.stop()
        healthMonitor.stop()
    }
}

fun buildBoostrapConfig(params: CryptoWorkerParams): SmartConfig {
    val databaseConfig = PathAndConfig(BootConfig.BOOT_DB_PARAMS, params.databaseParams)
    val cryptoConfig = PathAndConfig(BOOT_CRYPTO, params.cryptoParams)
    return getBootstrapConfig(
        params.defaultParams,  configurationValidatorFactory.createConfigValidator(), listOf(databaseConfig, cryptoConfig)
    ).addDefaultCryptoConfig(
        fallbackCryptoRootKey = KeyCredentials("root-passphrase", "root-salt"),
        fallbackSoftKey = KeyCredentials("soft-passphrase", "soft-salt")
    )
}

/** Additional parameters for the crypto worker are added here. */
class CryptoWorkerParams {
    @Mixin
    var defaultParams = DefaultWorkerParams()

    @CommandLine.Option(names = ["-d", "--databaseParams"], description = ["Database parameters for the worker."])
    var databaseParams = emptyMap<String, String>()

    @CommandLine.Option(names = ["--cryptoParams"], description = ["Crypto parameters for the worker."])
    var cryptoParams = emptyMap<String, String>()
}