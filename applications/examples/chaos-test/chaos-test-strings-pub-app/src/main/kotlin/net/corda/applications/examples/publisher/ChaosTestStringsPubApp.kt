package net.corda.applications.examples.testclients

// import net.corda.applications.common.ConfigHelper.Companion.SYSTEM_ENV_BOOTSTRAP_SERVERS_PATH
import com.typesafe.config.ConfigFactory
import net.corda.components.examples.publisher.RunChaosTestStringsPub
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.factory.PublisherFactory
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
class ChaosTestStringsPubApp @Activate constructor(
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
) : Application {

    private companion object {
        val log: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
    }

    private var lifeCycleCoordinator: LifecycleCoordinator? = null

    @Suppress("SpreadOperator")
    override fun startup(args: Array<String>) {
        consoleLogger.info("Starting publisher...")

        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)

        if (parameters.helpRequested) {
            CommandLine.usage(CliParameters(), System.out)
            shutdownOSGiFramework()
        } else {
            val instanceId = parameters.instanceId?.toInt()
            var publisher: RunChaosTestStringsPub? = null

            lifeCycleCoordinator =
                coordinatorFactory.createCoordinator<ChaosTestStringsPubApp>() { event: LifecycleEvent, _: LifecycleCoordinator ->
                    log.info("LifecycleEvent received: $event")
                    when (event) {
                        is StartEvent -> {
                            publisher!!.start()
                        }
                        is StopEvent -> {
                            publisher?.stop()
                            shutdownOSGiFramework()
                        }
                        else -> {
                            log.error("$event unexpected!")
                        }
                    }
                }

            publisher = RunChaosTestStringsPub(
                lifeCycleCoordinator!!,
                publisherFactory,
                instanceId,
                parameters.numberOfRecords.toInt(),
                parameters.numberOfKeys.toInt(),
                getBootstrapConfig(instanceId),
                parameters.msgPrefix,
                parameters.msgDelayMs,
                parameters.logPubMsgs
            )

            lifeCycleCoordinator!!.start()
            consoleLogger.info("Finished publishing")
        }
    }

    override fun shutdown() {
        consoleLogger.info("Stopping publisher")
        log.info("Stopping application")
        lifeCycleCoordinator?.stop()
    }

    private fun shutdownOSGiFramework() {
        shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
    }

    private fun getBootstrapConfig(instanceId: Int?): SmartConfig {
        // NOTE: this doesn't make sense as it's recursive.
        //return smartConfigFactory.create(getBootstrapConfig(instanceId))
        println(instanceId)
        val config = ConfigFactory.empty()
        return SmartConfigFactory.create(config).create(config)
    }
}

class CliParameters {
    @CommandLine.Option(
        names = ["--instanceId"],
        description = ["InstanceId for a transactional publisher, leave blank to use async publisher"]
    )
    var instanceId: String? = null

    @CommandLine.Option(names = ["--numberOfRecords"], description = ["Number of records to send per key."])
    lateinit var numberOfRecords: String

    @CommandLine.Option(
        names = ["--numberOfKeys"],
        description = ["Number of keys to use. total records sent = numberOfKeys * numberOfRecords"]
    )
    lateinit var numberOfKeys: String

    @CommandLine.Option(
        names = ["--msgPrefix"],
        description = ["Message prefix string"]
    )
    var msgPrefix = "ChaosTestMessage"

    @CommandLine.Option(
        names = ["--msgDelayMs"],
        description = ["Optional delay between publishing messages in ms"]
    )
    var msgDelayMs: Long = 0

    @CommandLine.Option(
        names = ["--logPubMsgs"],
        description = ["Added published messages to INFO logging level. This could be very verbose!"]
    )
    var logPubMsgs: Boolean = false

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false

    /*
    @CommandLine.Option(names = ["--kafka"], description = ["File containing Kafka connection properties"])
    var kafkaProperties: File? = null
     */
}
