package net.corda.p2p.config.publisher

import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.publish.factory.ConfigPublisherFactory
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

@Component
@Suppress("LongParameterList")
class P2PConfigPublisher @Activate constructor(
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = ConfigPublisherFactory::class)
    private val configPublisherFactory: ConfigPublisherFactory,
    @Reference(service = SmartConfigFactory::class)
    private val smartConfigFactory: SmartConfigFactory,
) : Application {
    companion object {
        private val consoleLogger: Logger = LoggerFactory.getLogger("Console")
    }

    override fun startup(args: Array<String>) {
        val arguments = CommonArguments(configPublisherFactory, smartConfigFactory)
        val commandLine = CommandLine(arguments)
        commandLine.isCaseInsensitiveEnumValuesAllowed = true
        try {
            @Suppress("SpreadOperator")
            commandLine.execute(*args)
        } catch (e: Exception) {
            consoleLogger.warn("Could not publish configuration", e)
        }

        shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
    }

    override fun shutdown() {
    }
}
