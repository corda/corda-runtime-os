package net.corda.cli.application

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

//import org.apache.logging.log4j.layout.template.json.JsonTemplateLayout


@CommandLine.Command(
    name = "fake",
    mixinStandardHelpOptions = true,
    description = ["Fake command"],
)
class FakeCommand : Runnable {
    override fun run() {
        println("Hello from fake command (println)")
        val logger: Logger = LoggerFactory.getLogger(this::class.java)
        logger.info("Hello from fake command")
        logger.warn("Hello from fake command (warning)")
        throw NotImplementedError("This command is not implemented yet")
    }
}
