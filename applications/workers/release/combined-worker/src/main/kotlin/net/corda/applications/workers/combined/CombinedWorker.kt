package net.corda.applications.workers.combined

import net.corda.application.dbsetup.PostgresDbSetup
import net.corda.applications.workers.workercommon.ApplicationBanner
import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.JavaSerialisationFilter
import net.corda.applications.workers.workercommon.PathAndConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getBootstrapConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getParams
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.loggerStartupInfo
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.printHelpOrVersion
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.setupMonitor
import net.corda.applications.workers.workercommon.WorkerMonitor
import net.corda.crypto.config.impl.createCryptoBootstrapParamsMap
import net.corda.crypto.core.CryptoConsts.SOFT_HSM_ID
import net.corda.libs.configuration.secret.SecretsServiceFactoryResolver
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.processors.crypto.CryptoProcessor
import net.corda.processors.db.DBProcessor
import net.corda.processors.flow.FlowProcessor
import net.corda.processors.member.MemberProcessor
import net.corda.processors.p2p.gateway.GatewayProcessor
import net.corda.processors.p2p.linkmanager.LinkManagerProcessor
import net.corda.processors.rpc.RPCProcessor
import net.corda.processors.uniqueness.UniquenessProcessor
import net.corda.schema.configuration.BootConfig.BOOT_CRYPTO
import net.corda.schema.configuration.BootConfig.BOOT_DB_PARAMS
import net.corda.schema.configuration.DatabaseConfig
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
        private val logger = contextLogger()
    }

    /** Parses the arguments, then initialises and starts the processors. */
    @Suppress("ComplexMethod")
    override fun startup(args: Array<String>) {
        logger.info("Combined worker starting.")
        logger.loggerStartupInfo(platformInfoProvider)

        applicationBanner.show("Combined Worker", platformInfoProvider)

        if (System.getProperty("co.paralleluniverse.fibers.verifyInstrumentation") == true.toString()) {
            logger.info("Quasar's instrumentation verification is enabled")
        }

        val params = getParams(args, CombinedWorkerParams())
        if (printHelpOrVersion(params.defaultParams, CombinedWorker::class.java, shutDownService)) return
        if (params.hsmId.isBlank()) {
            // the combined worker may use SOFT HSM by default unlike the crypto worker
            params.hsmId = SOFT_HSM_ID
        }
        val databaseConfig = PathAndConfig(BOOT_DB_PARAMS, params.databaseParams)
        val cryptoConfig = PathAndConfig(BOOT_CRYPTO, createCryptoBootstrapParamsMap(params.hsmId, params.masterWrappingKeyPassphrase, params.masterWrappingKeySalt))
        val (bootstrapConfig, configFactory) = getBootstrapConfig(
            secretsServiceFactoryResolver,
            params.defaultParams,
            configurationValidatorFactory.createConfigValidator(),
            listOf(databaseConfig, cryptoConfig)
        )

        val superUser = System.getenv("CORDA_DEV_POSTGRES_USER") ?: "postgres"
        val superUserPassword = System.getenv("CORDA_DEV_POSTGRES_PASSWORD") ?: "password"
        val dbUrl = if(bootstrapConfig.getConfig(BOOT_DB_PARAMS).hasPath(DatabaseConfig.JDBC_URL))
            bootstrapConfig.getConfig(BOOT_DB_PARAMS).getString(DatabaseConfig.JDBC_URL) else "jdbc:postgresql://localhost:5432/cordacluster"
        val dbName = dbUrl.split("/").last().split("?").first()
        val dbAdmin = if(bootstrapConfig.getConfig(BOOT_DB_PARAMS).hasPath(DatabaseConfig.DB_USER))
            bootstrapConfig.getConfig(BOOT_DB_PARAMS).getString(DatabaseConfig.DB_USER) else "user"
        val dbAdminPassword = if(bootstrapConfig.getConfig(BOOT_DB_PARAMS).hasPath(DatabaseConfig.DB_PASS))
            bootstrapConfig.getConfig(BOOT_DB_PARAMS).getString(DatabaseConfig.DB_PASS) else "password"
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
            secretsPassphrase,
        ).run(bootstrapConfig, configFactory)

        setupMonitor(workerMonitor, params.defaultParams, this.javaClass.simpleName)

        JavaSerialisationFilter.install()

        logger.info("CONFIG = $bootstrapConfig")

        cryptoProcessor.start(bootstrapConfig)
        dbProcessor.start(bootstrapConfig)
        uniquenessProcessor.start()
        flowProcessor.start(bootstrapConfig)
        memberProcessor.start(bootstrapConfig)
        rpcProcessor.start(bootstrapConfig)
        linkManagerProcessor.start(bootstrapConfig)
        gatewayProcessor.start(bootstrapConfig)
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

        workerMonitor.stop()
    }
}

/** Additional parameters for the combined worker are added here. */
private class CombinedWorkerParams {
    @Mixin
    var defaultParams = DefaultWorkerParams()

    @Option(names = ["-d", "--database-params"], description = ["Database parameters for the worker."])
    var databaseParams = emptyMap<String, String>()

    @Option(names = ["-r", "--rpc-params"], description = ["RPC parameters for the worker."])
    var rpcParams = emptyMap<String, String>()

    @Option(names = ["--hsm-id"], description = ["HSM ID which is handled by this worker instance."])
    var hsmId = ""

    // It is not appropriate to use command line options for passphrases and SALTs in production,
    // but combined worker is not a production configuration so we do allow it here.
    @Option(names = ["-P", "--master-wrapping-key-passphrase"], description = ["Crypto processor master wrapping key passphrase"])
    var masterWrappingKeyPassphrase: String = ""

    @Option(names = ["-S", "--master-wrapping-key-salt"], description = ["Crypto processor master wrapping key salt"])
    var masterWrappingKeySalt: String = ""
}
