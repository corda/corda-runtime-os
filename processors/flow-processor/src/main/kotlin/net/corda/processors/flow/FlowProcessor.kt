package net.corda.processors.flow

import com.typesafe.config.Config
import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger
import org.slf4j.LoggerFactory

// TODO - Pass in the command line arguments.
class FlowProcessor {

    private companion object {
        // TODO - This doesn't work. Only logging directly to console works.
        val logger: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
    }

    fun startup(config: Config) {
        logger.info(config.toString())
        consoleLogger.info(config.toString())
    }

    fun shutdown() {
        consoleLogger.info("Stopping Flow Worker application...")
    }
}