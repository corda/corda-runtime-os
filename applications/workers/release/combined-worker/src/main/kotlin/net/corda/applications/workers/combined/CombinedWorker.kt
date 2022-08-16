package net.corda.applications.workers.combined

import net.corda.application.dbsetup.PostgresDbSetup
import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.HealthMonitor
import net.corda.applications.workers.workercommon.JavaSerialisationFilter
import net.corda.applications.workers.workercommon.PathAndConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getBootstrapConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getParams
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.printHelpOrVersion
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.setUpHealthMonitor
import net.corda.crypto.config.impl.addDefaultBootCryptoConfig
import net.corda.crypto.core.aes.KeyCredentials
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.processors.crypto.CryptoProcessor
import net.corda.processors.db.DBProcessor
import net.corda.processors.uniqueness.UniquenessProcessor
import net.corda.processors.flow.FlowProcessor
import net.corda.processors.member.MemberProcessor
import net.corda.processors.p2p.gateway.GatewayProcessor
import net.corda.processors.p2p.linkmanager.LinkManagerProcessor
import net.corda.processors.rpc.RPCProcessor
import net.corda.schema.configuration.BootConfig.BOOT_CRYPTO
import net.corda.schema.configuration.BootConfig.BOOT_DB_PARAMS
import net.corda.schema.configuration.ConfigKeys
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
    @Reference(service = UniquenessProcessor::class)
    private val uniquenessProcessor: UniquenessProcessor,
    @Reference(service = FlowProcessor::class)
    private val flowProcessor: FlowProcessor,
    @Reference(service = RPCProcessor::class)
    private val rpcProcessor: RPCProcessor,
    @Reference(service = MemberProcessor::class)
    private val memberProcessor: MemberProcessor,
    @Reference(service = LinkManagerProcessor::class)
    private val linkManagerProcessor: LinkManagerProcessor,
    @Reference(service = GatewayProcessor::class)
    private val gatewayProcessor: GatewayProcessor,
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

    /** Parses the arguments, then initialises and starts the processors. */
    @Suppress("ComplexMethod")
    override fun startup(args: Array<String>) {
        logger.info("Combined worker starting.")

        if (System.getProperty("co.paralleluniverse.fibers.verifyInstrumentation") == true.toString()) {
            logger.info("Quasar's instrumentation verification is enabled")
        }

        val params = getParams(args, CombinedWorkerParams())
        if (printHelpOrVersion(params.defaultParams, CombinedWorker::class.java, shutDownService)) return

        val databaseConfig = PathAndConfig(BOOT_DB_PARAMS, params.databaseParams)
        val cryptoConfig = PathAndConfig(BOOT_CRYPTO, params.cryptoParams)
        val config = getBootstrapConfig(
            params.defaultParams,
            configurationValidatorFactory.createConfigValidator(),
            listOf(databaseConfig, cryptoConfig)
        ).addDefaultBootCryptoConfig(
            fallbackMasterWrappingKey = KeyCredentials("soft-passphrase", "soft-salt")
        )

        val superUser = System.getenv("CORDA_DEV_POSTGRES_USER") ?: "postgres"
        val superUserPassword = System.getenv("CORDA_DEV_POSTGRES_PASSWORD") ?: "password"
        val dbUrl = if(config.getConfig(BOOT_DB_PARAMS).hasPath(ConfigKeys.JDBC_URL))
            config.getConfig(BOOT_DB_PARAMS).getString(ConfigKeys.JDBC_URL) else "jdbc:postgresql://localhost:5432/alice"
        val dbName = dbUrl.split("/").last().split("?").first()
        val dbAdmin = if(config.getConfig(BOOT_DB_PARAMS).hasPath(ConfigKeys.DB_USER))
            config.getConfig(BOOT_DB_PARAMS).getString(ConfigKeys.DB_USER) else "user"
        val dbAdminPassword = if(config.getConfig(BOOT_DB_PARAMS).hasPath(ConfigKeys.DB_PASS))
            config.getConfig(BOOT_DB_PARAMS).getString(ConfigKeys.DB_PASS) else "password"
        val secretsSalt = params.defaultParams.secretsParams["salt"] ?: "salt"
        val secretsPassphrase = params.defaultParams.secretsParams["passphrase"] ?: "passphrase"

        PostgresDbSetup(
            dbUrl,
            superUser,
            superUserPassword,
            dbAdmin,
            dbAdminPassword,
            dbName,
            secretsSalt,
            secretsPassphrase
        ).run()

        setUpHealthMonitor(healthMonitor, params.defaultParams)

        JavaSerialisationFilter.install()

        logger.info("CONFIG = $config")

        cryptoProcessor.start(config)
        dbProcessor.start(config)
        uniquenessProcessor.start()
        flowProcessor.start(config)
        memberProcessor.start(config)
        rpcProcessor.start(config)
        linkManagerProcessor.start(config, false)
        gatewayProcessor.start(config, false)
    }

    override fun shutdown() {
        logger.info("Combined worker stopping.")

        cryptoProcessor.stop()
        uniquenessProcessor.stop()
        dbProcessor.stop()
        flowProcessor.stop()
        memberProcessor.stop()
        rpcProcessor.stop()
        linkManagerProcessor.stop()
        gatewayProcessor.stop()

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

    @Option(names = ["--cryptoParams"], description = ["Crypto parameters for the worker."])
    var cryptoParams = emptyMap<String, String>()
}
