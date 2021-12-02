package net.corda.applications.linkmanager

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import picocli.CommandLine
import picocli.CommandLine.Option
import kotlin.random.Random

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
        description = ["The kafka servers (default: \${DEFAULT-VALUE})"]
    )
    var kafkaServers = System.getenv("KAFKA_SERVERS") ?: "localhost:9092"

    @Option(
        names = ["--config-topic-name"],
        description = ["The config topic name (default: \${DEFAULT-VALUE})"]
    )
    var configTopicName = System.getenv("CONFIG_TOPIC") ?: "ConfigTopic"

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
                "messaging.kafka.common.bootstrap.servers",
                ConfigValueFactory.fromAnyRef(kafkaServers)
            )
            .withValue(
                "config.topic.name",
                ConfigValueFactory.fromAnyRef(configTopicName)
            ).withValue(
                "messaging.topic.prefix",
                ConfigValueFactory.fromAnyRef(topicPrefix)
            ).withValue(
                // The default value of poll timeout is quite high (6 seconds), so setting it to something lower.
                // Specifically, state & event subscriptions have an issue where they are polling with high timeout on events topic,
                // leading to slow syncing upon startup. See: https://r3-cev.atlassian.net/browse/CORE-3163
                "messaging.subscription.consumer.poll.timeout",
                ConfigValueFactory.fromAnyRef(100)
            )
    }
}
