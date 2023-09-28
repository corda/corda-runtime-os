package net.corda.cli.plugins.topicconfig

import net.corda.cli.api.CordaCliPlugin
import org.pf4j.Extension
import org.pf4j.Plugin
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

class TopicPlugin : Plugin() {

    companion object {
        val classLoader: ClassLoader = this::class.java.classLoader
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun start() {
        logger.debug("Topic plugin started.")
    }

    override fun stop() {
        logger.debug("Topic plugin stopped.")
    }

    @Extension
    @CommandLine.Command(
        name = "topic",
        subcommands = [Create::class],
        description = ["Plugin for Kafka topic operations."],
        mixinStandardHelpOptions = true
    )
    class Topic : CordaCliPlugin {

        @CommandLine.Option(
            names = ["-n", "--name-prefix"],
            description = ["Name prefix for topics"]
        )
        var namePrefix: String = ""
    }

}
