package net.corda.applications.workers.combined

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import net.corda.application.dbsetup.PostgresDbSetup
import net.corda.applications.workers.workercommon.ApplicationBanner
import net.corda.applications.workers.workercommon.BusType
import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.JavaSerialisationFilter
import net.corda.applications.workers.workercommon.PathAndConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.createConfigFromParams
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getBootstrapConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getParams
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.loggerStartupInfo
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.printHelpOrVersion
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.setupMonitor
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.setupWebserver
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
import net.corda.processors.flow.mapper.FlowMapperProcessor
import net.corda.processors.member.MemberProcessor
import net.corda.processors.p2p.gateway.GatewayProcessor
import net.corda.processors.p2p.linkmanager.LinkManagerProcessor
import net.corda.processors.persistence.PersistenceProcessor
import net.corda.processors.rest.RestProcessor
import net.corda.processors.scheduler.SchedulerProcessor
import net.corda.processors.token.cache.TokenCacheProcessor
import net.corda.processors.uniqueness.UniquenessProcessor
import net.corda.processors.verification.VerificationProcessor
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.DatabaseConfig
import net.corda.schema.configuration.MessagingConfig.StateManager
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE
import net.corda.tracing.configureTracing
import net.corda.tracing.shutdownTracing
import net.corda.web.api.WebServer
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option
import java.time.Duration


// We use a different port for the combined worker since it is often run on Macs, which 
// sometimes have our chosen container health port of 7000 used.

const val COMBINED_WORKER_MONITOR_PORT = 7004

/** A worker that starts all processors. */
@Suppress("Unused", "LongParameterList")
@Component(service = [Application::class])
class CombinedWorker @Activate constructor(
    @Reference(service = CryptoProcessor::class)
    private val cryptoProcessor: CryptoProcessor,
    @Reference(service = DBProcessor::class)
    private val dbProcessor: DBProcessor,
    @Reference(service = PersistenceProcessor::class)
    private val persistenceProcessor: PersistenceProcessor,
    @Reference(service = UniquenessProcessor::class)
    private val uniquenessProcessor: UniquenessProcessor,
    @Reference(service = TokenCacheProcessor::class)
    private val tokenCacheProcessor: TokenCacheProcessor,
    @Reference(service = FlowProcessor::class)
    private val flowProcessor: FlowProcessor,
    @Reference(service = FlowMapperProcessor::class)
    private val flowMapperProcessor: FlowMapperProcessor,
    @Reference(service = VerificationProcessor::class)
    private val verificationProcessor: VerificationProcessor,
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
    @Reference(service = WebServer::class)
    private val webServer: WebServer,
    @Reference(service = ConfigurationValidatorFactory::class)
    private val configurationValidatorFactory: ConfigurationValidatorFactory,
    @Reference(service = PlatformInfoProvider::class)
    val platformInfoProvider: PlatformInfoProvider,
    @Reference(service = ApplicationBanner::class)
    val applicationBanner: ApplicationBanner,
    @Reference(service = SecretsServiceFactoryResolver::class)
    val secretsServiceFactoryResolver: SecretsServiceFactoryResolver,
    @Reference(service = SchedulerProcessor::class)
    val schedulerProcessor: SchedulerProcessor,
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
        val dbUrl = params.databaseParams[DatabaseConfig.JDBC_URL] ?: "jdbc:postgresql://localhost:5432/cordacluster"
        // Add the config schema to the JDBC URL in the params so that any processors which need the JDBC URL are using
        // the config schema.
        params.addSchemaToJdbcUrl("CONFIG")
        params.addDatabaseParam(DatabaseConfig.JDBC_URL + "_messagebus", "$dbUrl?currentSchema=MESSAGEBUS")

        if (printHelpOrVersion(params.defaultParams, CombinedWorker::class.java, shutDownService)) return
        if (params.hsmId.isBlank()) {
            // the combined worker may use SOFT HSM by default unlike the crypto worker
            params.hsmId = SOFT_HSM_ID
        }
        val databaseConfig = PathAndConfig(BootConfig.BOOT_DB, params.databaseParams)
        val cryptoConfig = PathAndConfig(BootConfig.BOOT_CRYPTO, createCryptoBootstrapParamsMap(params.hsmId))
        val restConfig = PathAndConfig(BootConfig.BOOT_REST, params.restParams)

        var config = getBootstrapConfig(
            secretsServiceFactoryResolver,
            params.defaultParams,
            configurationValidatorFactory.createConfigValidator(),
            listOf(databaseConfig, cryptoConfig, restConfig),
            listOf(createConfigFromParams(BootConfig.BOOT_STATE_MANAGER, params.stateManagerParams))
        )

        val superUser = System.getenv("CORDA_DEV_POSTGRES_USER") ?: "postgres"
        val superUserPassword = System.getenv("CORDA_DEV_POSTGRES_PASSWORD") ?: "password"
        val dbName = dbUrl.split("/").last().split("?").first()
        val dbAdmin = if (config.getConfig(BootConfig.BOOT_DB).hasPath(DatabaseConfig.DB_USER))
            config.getConfig(BootConfig.BOOT_DB).getString(DatabaseConfig.DB_USER) else "user"
        val dbAdminPassword = if (config.getConfig(BootConfig.BOOT_DB).hasPath(DatabaseConfig.DB_PASS))
            config.getConfig(BootConfig.BOOT_DB).getString(DatabaseConfig.DB_PASS) else "password"

        // Default pool settings for State Manager
        if (config.hasPath(BootConfig.BOOT_STATE_MANAGER)) {
            config = config
                .withValue(StateManager.JDBC_POOL_MIN_SIZE, fromAnyRef(1))
                .withValue(StateManager.JDBC_POOL_MAX_SIZE, fromAnyRef(5))
                .withValue(StateManager.JDBC_POOL_IDLE_TIMEOUT_SECONDS, fromAnyRef(Duration.ofMinutes(2).toSeconds()))
                .withValue(StateManager.JDBC_POOL_MAX_LIFETIME_SECONDS, fromAnyRef(Duration.ofMinutes(30).toSeconds()))
                .withValue(StateManager.JDBC_POOL_KEEP_ALIVE_TIME_SECONDS, fromAnyRef(Duration.ZERO.toSeconds()))
                .withValue(StateManager.JDBC_POOL_VALIDATION_TIMEOUT_SECONDS, fromAnyRef(Duration.ofSeconds(5).toSeconds()))
        }

        // Part of DB setup is to generate defaults for the crypto code. That currently includes a
        // default master wrapping key passphrase and salt, which we want to keep secret, and so
        // cannot simply be written to the database in plaintext. So, we need to construct SmartConfig secrets
        // to include in the defaults, so we need to pass in a SmartConfigFactory since that's what we use to
        // make secrets.
        //
        // In the future, perhaps we can simply rely on the schema for crypto defaults, and not supply a
        // default passphrase and salt but instead require them to be specified.

        /**
         * isDbBusType is used to tell which Bus type we are using, so we know whether to use DATABASE specific methods
         */
        val isDbBusType: Boolean = params.defaultParams.messaging[BUS_TYPE] == BusType.DATABASE.name

        PostgresDbSetup(
            dbUrl,
            superUser,
            superUserPassword,
            dbAdmin,
            dbAdminPassword,
            dbName,
            isDbBusType,
            config.factory,
        ).run()

        webServer.setupWebserver(params.defaultParams)
        setupMonitor(workerMonitor, params.defaultParams, this.javaClass.simpleName)

        configureTracing("Combined Worker", params.defaultParams.zipkinTraceUrl, params.defaultParams.traceSamplesPerSecond)

        JavaSerialisationFilter.install()

        logger.info("CONFIG = $config")

        cryptoProcessor.start(config)
        dbProcessor.start(config)
        persistenceProcessor.start(config)
        uniquenessProcessor.start(config)
        tokenCacheProcessor.start(config)
        flowProcessor.start(config)
        flowMapperProcessor.start(config)
        verificationProcessor.start(config)
        memberProcessor.start(config)
        restProcessor.start(config)
        linkManagerProcessor.start(config)
        gatewayProcessor.start(config)
        schedulerProcessor.start(config)
    }

    override fun shutdown() {
        logger.info("Combined worker stopping.")

        cryptoProcessor.stop()
        uniquenessProcessor.stop()
        tokenCacheProcessor.stop()
        persistenceProcessor.stop()
        dbProcessor.stop()
        flowProcessor.stop()
        flowMapperProcessor.stop()
        verificationProcessor.stop()
        memberProcessor.stop()
        restProcessor.stop()
        linkManagerProcessor.stop()
        gatewayProcessor.stop()
        schedulerProcessor.stop()

        webServer.stop()
        shutdownTracing()
    }
}

/** Additional parameters for the combined worker are added here. */
private class CombinedWorkerParams {
    @Mixin
    var defaultParams = DefaultWorkerParams(COMBINED_WORKER_MONITOR_PORT)

    @Option(names = ["-d", "--${BootConfig.BOOT_DB}"], description = ["Database parameters for the worker."])
    var databaseParams = emptyMap<String, String>()

    @Option(names = ["-r", "--${BootConfig.BOOT_REST}"], description = ["REST parameters for the worker."])
    var restParams = emptyMap<String, String>()

    // TODO - remove when reviewing crypto config
    @Option(names = ["--hsm-id"], description = ["HSM ID which is handled by this worker instance."])
    var hsmId = ""

    @Option(names = ["-S", "--${BootConfig.BOOT_STATE_MANAGER}"], description = ["Configuration for the state manager."])
    var stateManagerParams = emptyMap<String, String>()

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
