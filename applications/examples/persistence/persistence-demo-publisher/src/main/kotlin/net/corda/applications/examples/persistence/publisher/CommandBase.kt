package net.corda.applications.examples.persistence.publisher

import net.corda.osgi.api.Shutdown
import org.osgi.framework.FrameworkUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

open class CommandBase(
    private val shutDownService: Shutdown
) {
    companion object {
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
    }

    @CommandLine.Option(
        names = ["-k", "--kafka"],
        paramLabel = "KAKFA",
        description = ["Kafka broker"]
    )
    var kafka: String = "kafka:9092"

    fun call(function: () -> (Unit)) {
        var exitCode = 0
        try {
            function()
        } catch (e: Exception) {
            consoleLogger.error(e.message)
            exitCode = 2
        } finally {
            consoleLogger.info("Shutting OSGi framework")
            shutdownOSGiFramework()
            consoleLogger.info("Exiting with $exitCode")
//            System.exit(exitCode)
        }
    }

    fun shutdownOSGiFramework() {
        shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
    }
}
