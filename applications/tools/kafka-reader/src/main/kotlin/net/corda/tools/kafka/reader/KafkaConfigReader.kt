package net.corda.tools.kafka.reader

import com.typesafe.config.ConfigFactory
import net.corda.comp.kafka.config.read.KafkaConfigRead
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.v5.base.util.contextLogger
import org.osgi.framework.BundleContext
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import picocli.CommandLine
import java.io.File

@Suppress("SpreadOperator")
@Component(immediate = true)
class KafkaConfigReader @Activate constructor(
    @Reference(service = KafkaConfigRead::class)
    private var configReader: KafkaConfigRead,
    @Reference(service = Shutdown::class)
    private var shutdown: Shutdown
) : Application {

    private companion object {
        private val logger: Logger = contextLogger()
    }

    override fun startup(args: Array<String>) {
        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)
        if (parameters.helpRequested) {
            CommandLine.usage(CliParameters(), System.out)
            shutdownOSGiFramework()
        } else {
            configReader.start(ConfigFactory.parseFile(parameters.configurationFile))
            logger.info("____________________________SLEEP______________________________________")
            while (!configReader.isRunning) { Thread.sleep(100) }
            shutdownOSGiFramework()
        }
    }

    override fun shutdown() {
        logger.info("Shutting down config reader")
    }

    private fun shutdownOSGiFramework() {
        val bundleContext: BundleContext? = FrameworkUtil.getBundle(KafkaConfigReader::class.java).bundleContext
        if (bundleContext != null) {
                shutdown.shutdown(bundleContext.bundle)
        }
    }
}

class CliParameters {

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false

    @CommandLine.Option(names = ["--config"], description = ["File containing configuration to be stored"])
    lateinit var configurationFile: File
}