package net.corda.applications.workers.combined

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import net.corda.application.dbsetup.PostgresDbSetup
import net.corda.applications.workers.workercommon.ApplicationBanner
import net.corda.applications.workers.workercommon.BusType
import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.Health
import net.corda.applications.workers.workercommon.JavaSerialisationFilter
import net.corda.applications.workers.workercommon.Metrics
import net.corda.applications.workers.workercommon.StateManagerConfigHelper.createStateManagerConfigFromClusterDb
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.createConfigFromParams
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getBootstrapConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getParams
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.loggerStartupInfo
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.printHelpOrVersion
import net.corda.crypto.config.impl.createCryptoBootstrapParamsMap
import net.corda.crypto.core.CryptoConsts.SOFT_HSM_ID
import net.corda.libs.configuration.secret.SecretsServiceFactoryResolver
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.registry.LifecycleRegistry
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
import net.corda.schema.configuration.BootConfig.BOOT_JDBC_URL
import net.corda.schema.configuration.BootConfig.BOOT_WORKER_SERVICE
import net.corda.schema.configuration.DatabaseConfig
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
    @Reference(service = LifecycleRegistry::class)
    private val lifecycleRegistry: LifecycleRegistry,
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
        private const val DEFAULT_BOOT_STATE_MANAGER_TYPE = "Database"
        private const val MESSAGE_BUS_CONFIG_PATH_SUFFIX = "_messagebus"
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

        val dbConfig = createConfigFromParams(BootConfig.BOOT_DB, params.databaseParams)
        val stateManagerConfig = createStateManagerConfigFromClusterDb(dbConfig)
        val preparedDbConfig = prepareDbConfig(dbConfig)

        if (printHelpOrVersion(params.defaultParams, CombinedWorker::class.java, shutDownService)) return
        if (params.hsmId.isBlank()) {
            // the combined worker may use SOFT HSM by default unlike the crypto worker
            params.hsmId = SOFT_HSM_ID
        }

        val extraConfigs = mutableListOf(preparedDbConfig,stateManagerConfig)
        extraConfigs.addAll(createExtraConfigs(params))

        val config = getBootstrapConfig(
            secretsServiceFactoryResolver,
            params.defaultParams,
            configurationValidatorFactory.createConfigValidator(),
            extraConfigs
        )

        val superUser = System.getenv("CORDA_DEV_POSTGRES_USER") ?: "postgres"
        val superUserPassword = System.getenv("CORDA_DEV_POSTGRES_PASSWORD") ?: "password"
        val dbName = dbUrl.split("/").last().split("?").first()
        val dbAdmin = if (config.getConfig(BootConfig.BOOT_DB).hasPath(DatabaseConfig.DB_USER))
            config.getConfig(BootConfig.BOOT_DB).getString(DatabaseConfig.DB_USER) else "user"
        val dbAdminPassword = if (config.getConfig(BootConfig.BOOT_DB).hasPath(DatabaseConfig.DB_PASS))
            config.getConfig(BootConfig.BOOT_DB).getString(DatabaseConfig.DB_PASS) else "password"

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

        Metrics.configure(
            webServer,
            this.javaClass.simpleName,
            params.defaultParams.metricsKeepNames?.toRegex(),
            params.defaultParams.metricsDropLabels?.toRegex()
        )
        Health.configure(webServer, lifecycleRegistry)
        configureTracing("Combined Worker", params.defaultParams.zipkinTraceUrl, params.defaultParams.traceSamplesPerSecond)

        JavaSerialisationFilter.install()

        logger.info("CONFIG = $config")

        webServer.start(params.defaultParams.workerServerPort)
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

    private fun createExtraConfigs(params: CombinedWorkerParams): List<Config> {
        val extraConfigs = mutableListOf(
            createConfigFromParams(BootConfig.BOOT_CRYPTO, createCryptoBootstrapParamsMap(params.hsmId)),
            createConfigFromParams(BootConfig.BOOT_REST, params.restParams),
            createConfigFromParams(BOOT_WORKER_SERVICE, params.workerEndpoints),
        )

        if (params.mediatorReplicasFlowSession != null) {
            extraConfigs.add(
                createConfigFromParams(
                    BOOT_WORKER_SERVICE,
                    mapOf("mediatorReplicas.flowSession" to params.mediatorReplicasFlowSession.toString())
                )
            )
        }

        if(params.mediatorReplicasFlowMapperSessionIn != null) {
            extraConfigs.add(
                createConfigFromParams(
                    BOOT_WORKER_SERVICE,
                    mapOf("mediatorReplicas.flowMapperSessionIn" to params.mediatorReplicasFlowMapperSessionIn.toString())
                )
            )
        }

        if(params.mediatorReplicasFlowMapperSessionOut != null) {
            extraConfigs.add(
                createConfigFromParams(
                    BOOT_WORKER_SERVICE,
                    mapOf("mediatorReplicas.flowMapperSessionOut" to params.mediatorReplicasFlowMapperSessionOut.toString())
                )
            )
        }

        return extraConfigs
    }

    /**
     * Sets the JDBC URL (as schema agnostic). It is the DB users responsibility to have set their search_path context
     * to be able to see whichever schema they need to see.
     */
    private fun prepareDbConfig(dbConfig: Config): Config {
        val tempJdbcUrl = dbConfig.getString(BOOT_JDBC_URL)
        return dbConfig
            .withValue(BOOT_JDBC_URL, fromAnyRef(tempJdbcUrl))
            .withValue(
                BOOT_JDBC_URL + MESSAGE_BUS_CONFIG_PATH_SUFFIX,
                fromAnyRef(tempJdbcUrl)
            )
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

    @Option(names = ["--serviceEndpoint"], description = ["Internal REST endpoints for Corda workers"])
    val workerEndpoints: Map<String, String> =
        listOf("crypto", "verification", "uniqueness", "persistence", "tokenSelection", "p2pLinkManager")
            .associate { "endpoints.$it" to "localhost:7004" }
            .toMap()

    @Option(names = ["--mediator-replicas-flow-session"], description = ["Sets the number of mediators that consume " +
            "flow.session messages"])
    var mediatorReplicasFlowSession: Int? = null

    @Option(names = ["--mediator-replicas-flow-session-in"], description = ["Sets the number of mediators that " +
            "consume flow.mapper.session.in messages"])
    var mediatorReplicasFlowMapperSessionIn: Int? = null

    @Option(names = ["--mediator-replicas-flow-session-out"], description = ["Sets the number of mediators that " +
            "consume flow.mapper.session.out messages"])
    var mediatorReplicasFlowMapperSessionOut: Int? = null
}
