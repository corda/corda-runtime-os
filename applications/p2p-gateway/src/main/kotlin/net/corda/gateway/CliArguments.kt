package net.corda.gateway

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import kotlin.random.Random
import net.corda.schema.configuration.BootConfig.BOOT_KAFKA_COMMON
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.BootConfig.TOPIC_PREFIX
import net.corda.schema.configuration.MessagingConfig
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
        names = ["-m", "--messagingParams"],
        description = ["Messaging parameters for the gateway."]
    )
    var messagingParams = emptyMap<String, String>()

    @Option(
        names = ["--topicPrefix"],
        description = ["The topic prefix (default: \${DEFAULT-VALUE})"]
    )
    var topicPrefix = System.getenv("TOPIC_PREFIX") ?: ""

    @Option(
        names = ["-i", "--instance-id"],
        description = ["The unique instance ID (default to random number)"]
    )
    var instanceId = System.getenv("INSTANCE_ID")?.toInt() ?: Random.nextInt()

    @Option(names = ["--without-stubs"])
    var withoutStubs = false

    val bootConfiguration: Config by lazy {
        val parsedMessagingParams = messagingParams.mapKeys { (key, _) -> "$BOOT_KAFKA_COMMON.${key.trim()}" }.toMutableMap()
        parsedMessagingParams.computeIfAbsent("$BOOT_KAFKA_COMMON.bootstrap.servers") {
            System.getenv("KAFKA_SERVERS") ?: "localhost:9092"
        }
        ConfigFactory
            .parseMap(parsedMessagingParams)
            .withValue(
                TOPIC_PREFIX,
                ConfigValueFactory.fromAnyRef(topicPrefix)
            ).withValue(
                MessagingConfig.Bus.BUS_TYPE,
                ConfigValueFactory.fromAnyRef("KAFKA")
            )
            .withValue(
                INSTANCE_ID,
                ConfigValueFactory.fromAnyRef(instanceId)
            )
    }
}
