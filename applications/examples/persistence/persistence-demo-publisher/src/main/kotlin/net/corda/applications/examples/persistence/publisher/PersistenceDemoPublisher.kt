package net.corda.applications.examples.persistence.publisher

import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import picocli.CommandLine

// This purpose of this simple cmd line app is to be able to publish test messages to Kakfa
//  for the Persistence Demo.
//  OSGi/Application base class Is sloooooooow to boot, so may not be suitable for cli "as is"
//  also we should allow for return codes.
@Component
class PersistenceDemoPublisher @Activate constructor(
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = SmartConfigFactory::class)
    private val smartConfigFactory: SmartConfigFactory,
) : Application {
    @Suppress("SpreadOperator")
    override fun startup(args: Array<String>) {
        val parameters = CliParameters(shutDownService)
        val cli = CommandLine(parameters)
        cli.addSubcommand(ClusterAdminCommand(smartConfigFactory, publisherFactory, shutDownService))
        cli.addSubcommand(ConfigAdminCommand(smartConfigFactory, publisherFactory, shutDownService))
        cli.execute(*args)
    }

    override fun shutdown() {
    }
}

class CliParameters(shutDownService: Shutdown) : CommandBase(shutDownService), Runnable {
    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false

    override fun run() {
        CommandLine.usage(this, System.out)
        shutdownOSGiFramework()
    }
}

