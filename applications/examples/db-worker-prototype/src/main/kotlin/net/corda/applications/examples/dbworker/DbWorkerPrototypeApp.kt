package net.corda.applications.examples.dbworker

import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.v5.base.util.contextLogger
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

@Component
@Suppress("LongParameterList")
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
) : Application {

    companion object {
        val log: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
    }

    private var lifeCycleCoordinator: LifecycleCoordinator? = null

    @Suppress("SpreadOperator")
    override fun startup(args: Array<String>) {
        consoleLogger.info("Starting persistence demo application...")
        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)

        if (parameters.helpRequested) {
            CommandLine.usage(CliParameters(), System.out)
            shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
        } else {
            //var clusterAdminEventSub: RunClusterAdminEventSubscription? = null
            //var configAdminEventSub: ConfigAdminSubscription? = null

            /*
            val config = smartConfigFactory.create(ConfigFactory.parseMap(
                mapOf(
                    ConfigConstants.KAFKA_BOOTSTRAP_SERVER to parameters.kafka,
                    ConfigConstants.TOPIC_PREFIX_CONFIG_KEY to ConfigConstants.TOPIC_PREFIX
                )
            ))
            val dbSource = PostgresDataSourceFactory().create(
                parameters.dbUrl,
                parameters.dbUser,
                parameters.dbPass)

            // NOTE: "for real", this would probably be pushed into the component rather than getting an EMF here,
            //  as the component knows what needs to/can be persisted
            val entityManagerFactory = entityManagerFactoryFactory.create(
                "cluster-config",
                listOf(ConfigState::class.java),
                DbEntityManagerConfiguration(dbSource),
            )*/

            log.info("Creating life cycle coordinator")
            lifeCycleCoordinator =
                coordinatorFactory.createCoordinator<DbWorkerPrototypeApp>(
                ) { event: LifecycleEvent, _: LifecycleCoordinator ->
                    log.info("LifecycleEvent received: $event")
                    when (event) {
                        is StartEvent -> {
                            consoleLogger.info("Starting kafka subscriptions from ${parameters.kafka}")
                            /*
                            clusterAdminEventSub = RunClusterAdminEventSubscription(
                                subscriptionFactory,
                                config,
                                1,
                                dbSource.connection,
                                schemaMigrator,
                                consoleLogger,
                            )*/
                            /*
                            configAdminEventSub = ConfigAdminSubscription(
                                subscriptionFactory,
                                config,
                                1,
                                entityManagerFactory,
                                consoleLogger,
                            )*/

                            //clusterAdminEventSub?.start()
                           // configAdminEventSub?.start()
                        }
                        is StopEvent -> {
                            //clusterAdminEventSub?.stop()
                            //configAdminEventSub?.stop()
                        }
                        else -> {
                            log.error("$event unexpected!")
                        }
                    }
                }
            log.info("Starting life cycle coordinator")
            lifeCycleCoordinator?.start()
            consoleLogger.info("Demo application started")
        }
    }

    override fun shutdown() {
        consoleLogger.info("Shutting down persistence demo application...")
        lifeCycleCoordinator?.stop()
    }
}

class CliParameters {
    @CommandLine.Option(
        names = ["-k", "--kafka"],
        paramLabel = "KAKFA",
        description = ["Kafka broker"]
    )
    var kafka: String = "kafka:9092"

    @Suppress("ForbiddenComment")
    @CommandLine.Option(
        names = ["-j", "--jdbc-url"],
        paramLabel = "JDBC URL",
        description = ["JDBC URL for cluster db"]
    )
    var dbUrl: String = "jdbc:postgresql://cluster-db:5432/cordacluster"
    @CommandLine.Option(
        names = ["-u", "--db-user"],
        paramLabel = "DB USER",
        description = ["Cluster DB username"]
    )
    var dbUser: String = "user"
    @CommandLine.Option(
        names = ["-p", "--db-password"],
        paramLabel = "DB PASSWORD",
        description = ["Cluster DB password"]
    )
    var dbPass: String = "password"

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false
}