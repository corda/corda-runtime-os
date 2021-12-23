package net.corda.p2p.config.publisher

import com.typesafe.config.Config
import net.corda.libs.configuration.publish.CordaConfigurationKey
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine.Option
import picocli.CommandLine.ParentCommand

abstract class ConfigProducer : Runnable {
    companion object {
        private val consoleLogger: Logger = LoggerFactory.getLogger("Console")
    }

    @ParentCommand
    private val parent: CommonArguments? = null

    @Option(names = ["-h", "--help"], usageHelp = true, description = ["display this help message"])
    var usageHelpRequested = false

    override fun run() {
        consoleLogger.info("Publishing configuration...")
        val writer = parent!!.createPublisher()
        writer.updateConfiguration(
            key,
            parent.smartConfigFactory.create(configuration)
        )
        consoleLogger.info("Configuration Published")
    }

    protected abstract val configuration: Config

    protected abstract val key: CordaConfigurationKey
}
