package net.corda.applications.examples.persistence.config.admin

import com.typesafe.config.ConfigFactory
import net.corda.components.examples.persistence.config.admin.ClusterConfig
import net.corda.components.examples.persistence.config.admin.ConfigAppSubscription
import net.corda.db.core.PostgresDataSourceFactory
import net.corda.lifecycle.*
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.impl.InMemoryEntityManagerConfiguration
import net.corda.osgi.api.Application
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

@Component
class ConfigSubscriptionApp @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = EntityManagerFactoryFactory::class)
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory
) : Application {

    companion object {
        val log: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
    }

    private var lifeCycleCoordinator: LifecycleCoordinator? = null
    private val entityManagerFactory = entityManagerFactoryFactory.create(
        "cluster-config",
        listOf(ClusterConfig::class.java),
        InMemoryEntityManagerConfiguration("cluster-config"),
    )

    // TODO: add helpRequested use case?
    override fun startup(args: Array<String>) {
        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)

        val instanceId = 1
        var configAdminEventSub: ConfigAppSubscription? = null

        val dbConnection = PostgresDataSourceFactory().create(
            parameters.dbUrl,
            parameters.dbUser,
            parameters.dbPass)

        val config = ConfigFactory.parseMap(
            mapOf(
                ConfigConstants.KAFKA_BOOTSTRAP_SERVER to parameters.kafka,
                ConfigConstants.TOPIC_PREFIX_CONFIG_KEY to ConfigConstants.TOPIC_PREFIX
            )
        )

        lifeCycleCoordinator = coordinatorFactory.createCoordinator<String>(
        ) { event: LifecycleEvent, _: LifecycleCoordinator ->
            log.info("LifecycleEvent received: $event")
            when (event) {
                is StartEvent -> {
                    consoleLogger.info("Starting kafka subscriptions from ${parameters.kafka}")
                    configAdminEventSub = ConfigAppSubscription(subscriptionFactory, config, 1, dbConnection.connection, entityManagerFactory)
                    configAdminEventSub!!.start()
                }
                is StopEvent -> {
                    configAdminEventSub!!.stop()
                }
                else -> {
                    log.error("$event unexpected!")
                }
            }
        }

        log.info("Starting life cycle coordinator")
        lifeCycleCoordinator!!.start()
        consoleLogger.info("Config admin application started, finished publishing")
    }

    override fun shutdown() {
        consoleLogger.info("Stopping publisher")
        log.info("Stopping config admin application")
        lifeCycleCoordinator?.stop()
    }
}

class CliParameters {
    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false

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

    @CommandLine.Option(
        names = ["-k", "--kafka"],
        paramLabel = "KAKFA",
        description = ["Kafka broker"]
    )
    var kafka: String = "kafka:9092"
}