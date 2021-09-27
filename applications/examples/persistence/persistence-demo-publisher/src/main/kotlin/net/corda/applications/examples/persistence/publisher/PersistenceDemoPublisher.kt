package net.corda.applications.examples.persistence.publisher

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
import java.util.UUID

// This purpose of this simple cmd line app is to be able to publish test messages to Kakfa
//  for the Persistence Demo.
//  OSGi/Application base class Is sloooooooow to boot, so may not be suitable for cli "as is"
//  also we should allow for return codes.
@Component
class PersistenceDemoPublisher  @Activate constructor(
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
) : Application {
    private companion object {
        val log: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
    }

    override fun startup(args: Array<String>) {
        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)

        if (parameters.helpRequested) {
            CommandLine.usage(CliParameters(), System.out)
        } else {
            consoleLogger.info("Publishing cluster-admin event to ${parameters.kafka}")
            val publisher = KafkaPublisher(parameters.kafka, publisherFactory)
            publisher.publish("cluster-admin-event", UUID.randomUUID().toString(), "Hello")
        }
        shutdownOSGiFramework()
    }

    override fun shutdown() {
        consoleLogger.info("Complete")
    }

    private fun shutdownOSGiFramework() {
        shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
    }
}
//@Command(
//    name = "publish",
//    mixinStandardHelpOptions = true,
//    description = ["Send the ClusterAdmin Message"])
//class DemoApp : Callable<Int> {
//    @CommandLine.Option(names = ["-k", "--kafka"], paramLabel = "KAKFA",
//        description = ["Kafka broker"])
//    private var kafka: String = "kafka:9092"
//
//    override fun call(): Int {
//        return call { println(kafka) }
//    }
//
//    @Command(
//        name = "cluster-admin",
//        mixinStandardHelpOptions = true,
//        description = ["Publish a cluster admin message"])
//    fun publishClusterAdmin(): Int {
//        return call {
//            val publisher = KafkaPublisher(kafka)
//            publisher.publish("cluster-admin-event", UUID.randomUUID().toString(), "Hello")
//        }
//    }
//
//    private fun call(function: () -> (Unit)) : Int {
//        try {
//            function()
//        }
//        catch(e: Exception) {
//            println(e.message)
//            return 1
//        }
//        return 0
//    }
//}

class CliParameters {
    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false

    @CommandLine.Option(
        names = ["-k", "--kafka"],
        paramLabel = "KAKFA",
        description = ["Kafka broker"])
    var kafka: String = "kafka:9092"
}
