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
import net.corda.processors.rest.RestProcessor
import net.corda.processors.uniqueness.UniquenessProcessor
import net.corda.schema.configuration.BootConfig.BOOT_CRYPTO
import net.corda.schema.configuration.BootConfig.BOOT_DB_PARAMS
import net.corda.schema.configuration.DatabaseConfig
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
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
    @Reference(service = RestProcessor::class)
    private val restProcessor: RestProcessor,
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
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
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
        // Extract the schemaless db url from the params, the combined worker needs this to set up all the schemas which
        // it does in the same db.
        val dbUrl = checkNotNull(params.databaseParams[DatabaseConfig.JDBC_URL])
        // Add the config schema to the JDBC URL in the params so that any processors which need the JDBC URL are using
        // the config schema.
        params.addSchemaToJdbcUrl("CONFIG")
        params.addDatabaseParam(DatabaseConfig.JDBC_URL + "_messagebus", dbUrl + "?currentSchema=MESSAGEBUS")

        if (printHelpOrVersion(params.defaultParams, CombinedWorker::class.java, shutDownService)) return
        if (params.hsmId.isBlank()) {
            // the combined worker may use SOFT HSM by default unlike the crypto worker
            params.hsmId = SOFT_HSM_ID
        }
        val databaseConfig = PathAndConfig(BOOT_DB_PARAMS, params.databaseParams)
        val cryptoConfig = PathAndConfig(BOOT_CRYPTO, createCryptoBootstrapParamsMap(params.hsmId))
        val config = getBootstrapConfig(
            secretsServiceFactoryResolver,
            params.defaultParams,
            configurationValidatorFactory.createConfigValidator(),
            listOf(databaseConfig, cryptoConfig)
        )

        val superUser = System.getenv("CORDA_DEV_POSTGRES_USER") ?: "postgres"
        val superUserPassword = System.getenv("CORDA_DEV_POSTGRES_PASSWORD") ?: "password"
        val dbName = dbUrl.split("/").last().split("?").first()
        val dbAdmin = if(config.getConfig(BOOT_DB_PARAMS).hasPath(DatabaseConfig.DB_USER))
            config.getConfig(BOOT_DB_PARAMS).getString(DatabaseConfig.DB_USER) else "user"
        val dbAdminPassword = if(config.getConfig(BOOT_DB_PARAMS).hasPath(DatabaseConfig.DB_PASS))
            config.getConfig(BOOT_DB_PARAMS).getString(DatabaseConfig.DB_PASS) else "password"
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

        setupMonitor(workerMonitor, params.defaultParams, this.javaClass.simpleName)

        JavaSerialisationFilter.install()

        logger.info("CONFIG = $config")

        cryptoProcessor.start(config)
        dbProcessor.start(config)
        uniquenessProcessor.start()
        flowProcessor.start(config)
        memberProcessor.start(config)
        restProcessor.start(config)
        linkManagerProcessor.start(config)
        gatewayProcessor.start(config)
    }

    override fun shutdown() {
        logger.info("Combined worker stopping.")

        cryptoProcessor.stop()
        uniquenessProcessor.stop()
        dbProcessor.stop()
        flowProcessor.stop()
        memberProcessor.stop()
        restProcessor.stop()
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

    /**
     * Combined worker parameter for JDBC URL should be the schemaless database URL because the combined worker sets up
     * schemas itself. However, Corda processors all expect the JDBC URL in the config to point to the config schema
     * directly, so the name of that schema must be added to the params that are used to create the config.
     */
    fun addSchemaToJdbcUrl(schema: String) {
        val databaseParamsWithSchema = databaseParams.toMutableMap()
        databaseParamsWithSchema[DatabaseConfig.JDBC_URL] += "?currentSchema=$schema"
        databaseParams = databaseParamsWithSchema.toMap()
    }

    fun addDatabaseParam(key: String, value: String) {
        databaseParams += Pair(key, value)
    }
}
