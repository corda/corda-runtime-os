package net.corda.applications.examples.dbworker

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.core.PostgresDataSourceFactory
import net.corda.db.schema.DbSchema
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.permissions.storage.common.ConfigKeys.DB_CONFIG_KEY
import net.corda.libs.permissions.storage.common.ConfigKeys.DB_PASSWORD
import net.corda.libs.permissions.storage.common.ConfigKeys.DB_URL
import net.corda.libs.permissions.storage.common.ConfigKeys.DB_USER
import net.corda.libs.permissions.storage.reader.factory.PermissionStorageReaderFactory
import net.corda.libs.permissions.storage.writer.factory.PermissionStorageWriterProcessorFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.orm.EntitiesSet
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.permissions.cache.PermissionCacheService
import net.corda.permissions.storage.reader.PermissionStorageReaderService
import net.corda.permissions.storage.writer.PermissionStorageWriterService
import net.corda.v5.base.util.contextLogger
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.StringWriter
import java.util.*
import javax.sql.DataSource

@Component
@Suppress("LongParameterList", "UNUSED")
class DbWorkerPrototypeApp @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = LiquibaseSchemaMigrator::class)
    private val schemaMigrator: LiquibaseSchemaMigrator,
    @Reference(service = EntityManagerFactoryFactory::class)
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory,
    @Reference(service = SmartConfigFactory::class)
    private val smartConfigFactory: SmartConfigFactory,
    @Reference(service = PermissionStorageWriterProcessorFactory::class)
    private val permissionStorageWriterProcessorFactory: PermissionStorageWriterProcessorFactory,
    @Reference(service = PermissionCacheService::class)
    private val permissionCacheService: PermissionCacheService,
    @Reference(service = PermissionStorageReaderFactory::class)
    private val permissionStorageReaderFactory: PermissionStorageReaderFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = EntitiesSet::class, name = DbSchema.RPC_RBAC)
    private val rbacEntitiesSet: EntitiesSet,
    @Reference(service = PermissionStorageReaderService::class)
    private val permissionStorageReaderService: PermissionStorageReaderService,
    @Reference(service = PermissionStorageWriterService::class)
    private val permissionStorageWriterService: PermissionStorageWriterService
) : Application {

    private companion object {
        val log: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")

        const val BOOTSTRAP_SERVERS = "bootstrap.servers"
        const val KAFKA_COMMON_BOOTSTRAP_SERVER = "messaging.kafka.common.bootstrap.servers"

        const val TOPIC_PREFIX = "messaging.topic.prefix"
        const val CONFIG_TOPIC_NAME = "config.topic.name"
    }

    private var lifeCycleCoordinator: LifecycleCoordinator? = null

    @Suppress("SpreadOperator")
    override fun startup(args: Array<String>) {
        consoleLogger.info("DB Worker prototype application starting")
        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)

        if (parameters.helpRequested) {
            CommandLine.usage(CliParameters(), System.out)
            shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
        } else {
            log.info("Creating life cycle coordinator")
            lifeCycleCoordinator =
                coordinatorFactory.createCoordinator<DbWorkerPrototypeApp>(
                ) { event: LifecycleEvent, _: LifecycleCoordinator ->
                    log.info("LifecycleEvent received: $event")
                    when (event) {
                        is StartEvent -> {
                            consoleLogger.info("Received start event")
                        }
                        is StopEvent -> {
                            consoleLogger.info("Received stop event")
                        }
                        else -> {
                            log.error("$event unexpected!")
                        }
                    }
                }
            log.info("Starting life cycle coordinator")
            lifeCycleCoordinator?.start()

            if (parameters.dbUrl.isBlank()) {
                consoleLogger.error("DB connectivity details were not provided")
                shutdown()
                return
            }

            consoleLogger.info("DB to be used: ${parameters.dbUrl}")
            val dbSource = PostgresDataSourceFactory().create(
                parameters.dbUrl,
                parameters.dbUser,
                parameters.dbPass
            )
            applyLiquibaseSchema(dbSource)

            val bootstrapConfig: SmartConfig = getBootstrapConfig(
                null, parameters.dbUrl,
                parameters.dbUser,
                parameters.dbPass
            )

            log.info("Starting configuration read service with bootstrap config ${bootstrapConfig}.")
            configurationReadService.start()
            configurationReadService.bootstrapConfig(bootstrapConfig)

            log.info("Starting PermissionCacheService")
            permissionCacheService.start()

            log.info("Starting PermissionStorageReaderService")
            permissionStorageReaderService.start()

            log.info("Starting PermissionStorageWriterService")
            permissionStorageWriterService.start()

            consoleLogger.info("DB Worker prototype application fully started")
        }
    }

    private fun applyLiquibaseSchema(dbSource: DataSource) {
        val schemaClass = DbSchema::class.java
        val bundle = FrameworkUtil.getBundle(schemaClass)
        log.info("RBAC schema bundle $bundle")

        val fullName = schemaClass.packageName + ".rbac"
        val resourcePrefix = fullName.replace('.', '/')
        val cl = ClassloaderChangeLog(
            linkedSetOf(
                ClassloaderChangeLog.ChangeLogResourceFiles(
                    fullName,
                    listOf("$resourcePrefix/db.changelog-master.xml"),
                    classLoader = schemaClass.classLoader
                )
            )
        )
        StringWriter().use {
            // Cannot use DbSchema.RPC_RBAC schema for LB here as this schema needs to be created ahead of change
            // set being applied
            schemaMigrator.createUpdateSql(dbSource.connection, cl, it, LiquibaseSchemaMigrator.PUBLIC_SCHEMA)
            log.info("Schema creation SQL: $it")
        }
        schemaMigrator.updateDb(dbSource.connection, cl, LiquibaseSchemaMigrator.PUBLIC_SCHEMA)

        log.info("Liquibase schema applied")
    }

    private fun getBootstrapConfig(
        kafkaConnectionProperties: Properties?,
        dbUrl: String,
        dbUser: String,
        dbPass: String
    ): SmartConfig {

        val bootstrapServer = getConfigValue(kafkaConnectionProperties, BOOTSTRAP_SERVERS)
        return smartConfigFactory.create(
            ConfigFactory.empty()
                .withValue(KAFKA_COMMON_BOOTSTRAP_SERVER, ConfigValueFactory.fromAnyRef(bootstrapServer))
                .withValue(
                    CONFIG_TOPIC_NAME,
                    ConfigValueFactory.fromAnyRef(getConfigValue(kafkaConnectionProperties, CONFIG_TOPIC_NAME))
                )
                .withValue(
                    TOPIC_PREFIX,
                    ConfigValueFactory.fromAnyRef(getConfigValue(kafkaConnectionProperties, TOPIC_PREFIX, ""))
                )
                .withValue(
                    DB_CONFIG_KEY,
                    ConfigValueFactory.fromMap(mapOf(DB_URL to dbUrl, DB_USER to dbUser, DB_PASSWORD to dbPass))
                )
        )
    }

    private fun getConfigValue(properties: Properties?, path: String, default: String? = null): String {
        var configValue = System.getProperty(path)
        if (configValue == null && properties != null) {
            configValue = properties[path].toString()
        }

        if (configValue == null) {
            if (default != null) {
                return default
            }
            log.error("No $path property found! Pass property in via --kafka properties file or via -D$path")
            shutdown()
        }
        return configValue
    }

    override fun shutdown() {
        consoleLogger.info("Shutting down DB Worker prototype application")
        lifeCycleCoordinator?.stop()
        lifeCycleCoordinator = null
        permissionStorageWriterService.stop()
        permissionStorageReaderService.stop()
        permissionCacheService.stop()
    }
}

class CliParameters {
    @CommandLine.Option(
        names = ["-k", "--kafka"],
        paramLabel = "KAKFA",
        description = ["Kafka broker"]
    )
    var kafka: String = ""

    @Suppress("ForbiddenComment")
    @CommandLine.Option(
        names = ["-j", "--jdbc-url"],
        paramLabel = "JDBC URL",
        description = ["JDBC URL for cluster db"]
    )
    var dbUrl: String = ""
    @CommandLine.Option(
        names = ["-u", "--db-user"],
        paramLabel = "DB USER",
        description = ["Cluster DB username"]
    )
    var dbUser: String = ""
    @CommandLine.Option(
        names = ["-p", "--db-password"],
        paramLabel = "DB PASSWORD",
        description = ["Cluster DB password"]
    )
    var dbPass: String = ""

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false
}