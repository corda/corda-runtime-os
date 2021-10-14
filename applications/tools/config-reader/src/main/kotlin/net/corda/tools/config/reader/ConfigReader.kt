package net.corda.tools.config.reader

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfigFactory
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
class ConfigReader @Activate constructor(
    @Reference(service = ConfigurationReadService::class)
    private var configurationReadService: ConfigurationReadService,
    @Reference(service = Shutdown::class)
    private var shutdown: Shutdown,
    @Reference(service = SmartConfigFactory::class)
    private var smartConfigFactory: SmartConfigFactory,
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
            val conf = smartConfigFactory.create(ConfigFactory.parseFile(parameters.configurationFile))
            configurationReadService.bootstrapConfig(conf)
            configurationReadService.registerForUpdates { changedKeys, config ->
                logger.info("New configuration received for $changedKeys")
                logger.info("Full configuration: $config")
            }
            configurationReadService.start()
            while (!configurationReadService.isRunning) { Thread.sleep(100) }
            shutdownOSGiFramework()
        }
    }

    override fun shutdown() {
        logger.info("Shutting down config reader")
        configurationReadService.stop()
    }

    private fun shutdownOSGiFramework() {
        val bundleContext: BundleContext? = FrameworkUtil.getBundle(ConfigReader::class.java).bundleContext
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
