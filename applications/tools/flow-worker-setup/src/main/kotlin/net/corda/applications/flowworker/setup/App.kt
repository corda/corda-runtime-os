package net.corda.applications.flowworker.setup

import net.corda.applications.flowworker.setup.tasks.Tasks
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.osgi.api.Application
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

@Suppress("Unused")
@Component
class App @Activate constructor(
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
)
    : Application {

    private companion object {
        val log : Logger = LoggerFactory.getLogger("App")
    }

    @Suppress("SpreadOperator")
    override fun startup(args: Array<String>) {
        log.info("Starting")
        val parameters = Args()
        CommandLine(parameters).parseArgs(*args)


        val context = TaskContext(parameters, log, publisherFactory)
        Tasks(context, log).execute(parameters.tasks)
    }

    override fun shutdown() {
        log.info("Closing..")
    }
}
