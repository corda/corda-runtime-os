package net.corda.tools.kafka.reader

import com.typesafe.config.ConfigFactory
import net.corda.comp.kafka.config.read.KafkaConfigRead
import net.corda.osgi.api.Application
import net.corda.v5.base.util.contextLogger
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
) : Application {

    private companion object {
        private val logger: Logger = contextLogger()
    }

    override fun run(args: Array<String>) : Int {
        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)
        if (parameters.helpRequested) {
            CommandLine.usage(CliParameters(), System.out)
        } else {
            configReader.start(ConfigFactory.parseFile(parameters.configurationFile))
            logger.info("____________________________SLEEP______________________________________")
            while (!configReader.isRunning) { Thread.sleep(100) }
        }
        return 0
    }
}

class CliParameters {

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false

    @CommandLine.Option(names = ["--config"], description = ["File containing configuration to be stored"])
    lateinit var configurationFile: File
}