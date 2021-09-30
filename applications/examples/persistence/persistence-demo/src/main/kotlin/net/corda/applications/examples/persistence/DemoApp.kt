package net.corda.applications.examples.persistence

import com.typesafe.config.ConfigFactory
import net.corda.components.examples.persistence.cluster.admin.RunClusterAdminEventSubscription
import net.corda.components.examples.persistence.cluster.admin.processor.ClusterAdminEventProcessor
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.core.PostgresDataSourceFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
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
class DemoApp @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = LiquibaseSchemaMigrator::class)
    private val schemaMigrator: LiquibaseSchemaMigrator,
) : Application {

    companion object {
        val log: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
    }

    private var lifeCycleCoordinator: LifecycleCoordinator? = null

    override fun startup(args: Array<String>) {
        consoleLogger.info("Starting persistence demo application...")
        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)

        if (parameters.helpRequested) {
            CommandLine.usage(CliParameters(), System.out)
            shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
        } else {
            var clusterAdminEventSub: RunClusterAdminEventSubscription? = null

            val config = ConfigFactory.parseMap(
                mapOf(
                    ConfigConstants.KAFKA_BOOTSTRAP_SERVER to parameters.kafka,
                    ConfigConstants.TOPIC_PREFIX_CONFIG_KEY to ConfigConstants.TOPIC_PREFIX
                )
            )
            val dbSource = PostgresDataSourceFactory().create(
                parameters.dbUrl,
                parameters.dbUser,
                parameters.dbPass)

            log.info("Creating life cycle coordinator")
            lifeCycleCoordinator =
                coordinatorFactory.createCoordinator<DemoApp>(
                ) { event: LifecycleEvent, _: LifecycleCoordinator ->
                    log.info("LifecycleEvent received: $event")
                    when (event) {
                        is StartEvent -> {
                            consoleLogger.info("Starting kafka subscriptions from ${parameters.kafka}")
                            clusterAdminEventSub = RunClusterAdminEventSubscription(
                                subscriptionFactory,
                                config,
                                1,
                                dbSource.connection,
                                schemaMigrator,
                                consoleLogger,
                            )
                            clusterAdminEventSub?.start()
                        }
                        is StopEvent -> {
                            clusterAdminEventSub?.stop()
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

    // TODO: cluster DB config should maybe be taken from the Kafka message instead of passed in?
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