package net.corda.applications.examples.persistence

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
)  : Application {

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
            log.info("Creating life cycle coordinator")
            lifeCycleCoordinator =
                coordinatorFactory.createCoordinator<DemoApp>(
                ) { event: LifecycleEvent, _: LifecycleCoordinator ->
                    log.info("LifecycleEvent received: $event")
                    when (event) {
                        is StartEvent -> {
                            consoleLogger.info("Starting kafka subscriptions from ${parameters.kafka}")

                        }
                        is StopEvent -> {
                        }
                        else -> {
                            log.error("$event unexpected!")
                        }
                    }
                }
            log.info("Starting life cycle coordinator")
            lifeCycleCoordinator!!.start()
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

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false
}