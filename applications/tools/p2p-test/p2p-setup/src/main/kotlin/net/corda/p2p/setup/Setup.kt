package net.corda.p2p.setup

import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.v5.base.util.contextLogger
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import picocli.CommandLine

@Component(immediate = true)
class Setup @Activate constructor(
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
) : Application {
    private companion object {
        private val logger = contextLogger()
    }

    override fun startup(args: Array<String>) {
        val command = Command()
        val commandLine = CommandLine(command)
            .setCaseInsensitiveEnumValuesAllowed(true)
        @Suppress("SpreadOperator")
        val exitCode = commandLine.execute(*args)
        if (exitCode != 0) {
            throw SetupException("Error in setup")
        }
        val records = commandLine.parseResult.subcommands().mapNotNull {
            it.commandSpec().commandLine().getExecutionResult<Any?>() as? List<*>
        }.flatten()
            .filterIsInstance<Record<*, *>>()

        if (records.isNotEmpty()) {
            publisherFactory.createPublisher(
                PublisherConfig("p2p-setup"),
                command.nodeConfiguration(),
            ).use { publisher ->
                logger.info("Publishing ${records.size} records")
                publisher.publish(records).forEach {
                    it.join()
                }
                logger.info("Published ${records.size} records")
            }
        }

        shutdown()
    }

    override fun shutdown() {
        shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
    }
}
