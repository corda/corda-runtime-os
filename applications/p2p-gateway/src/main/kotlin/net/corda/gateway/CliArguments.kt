package net.corda.gateway

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import kotlin.random.Random
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.BootConfig.TOPIC_PREFIX
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE
import net.corda.schema.configuration.MessagingConfig.Bus.KAFKA_BOOTSTRAP_SERVERS
import picocli.CommandLine
import picocli.CommandLine.Option

internal class CliArguments {
    companion object {
        fun parse(args: Array<String>): CliArguments {
            val arguments = CliArguments()
            val commandLine = CommandLine(arguments)
            commandLine.isCaseInsensitiveEnumValuesAllowed = true
            @Suppress("SpreadOperator")
            commandLine.parseArgs(*args)

            if (arguments.helpRequested) {
                commandLine.usage(System.out)
            }

            return arguments
        }
    }
    @Option(
        names = ["-h", "--help"],
        usageHelp = true,
        description = ["Display help and exit"]
    )
    var helpRequested = false

    @Option(
        names = ["-k", "--kafka-servers"],
        description = ["A comma-separated list of addresses of Kafka brokers (default: \${DEFAULT-VALUE})"]
    )
    var kafkaServers = System.getenv("KAFKA_SERVERS") ?: "localhost:9092"

    @Option(
        names = ["--topic-prefix"],
        description = ["The topic prefix (default: \${DEFAULT-VALUE})"]
    )
    var topicPrefix = System.getenv("TOPIC_PREFIX") ?: ""

    @Option(
        names = ["-i", "--instance-id"],
        description = ["The unique instance ID (default to random number)"]
    )
    var instanceId = System.getenv("INSTANCE_ID")?.toInt() ?: Random.nextInt()

    val kafkaNodeConfiguration: Config by lazy {
        ConfigFactory.empty()
            .withValue(
                KAFKA_BOOTSTRAP_SERVERS,
                ConfigValueFactory.fromAnyRef(kafkaServers)
            )
            .withValue(
                BUS_TYPE,
                ConfigValueFactory.fromAnyRef("KAFKA")
            )
            .withValue(
                TOPIC_PREFIX,
                ConfigValueFactory.fromAnyRef(topicPrefix)
            )
            .withValue(
                INSTANCE_ID,
                ConfigValueFactory.fromAnyRef(instanceId)
            )
    }
}
