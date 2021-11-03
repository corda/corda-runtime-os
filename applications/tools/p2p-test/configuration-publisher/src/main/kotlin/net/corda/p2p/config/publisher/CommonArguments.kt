package net.corda.p2p.config.publisher

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.write.ConfigWriter
import net.corda.libs.configuration.write.factory.ConfigWriterFactory
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    subcommands = [
        GatewayConfiguration::class,
        LinkManagerConfiguration::class,
    ],
    description = [
        "Publish configuration to the P2P applications"
    ]
)
internal class CommonArguments(
    private val configWriterFactory: ConfigWriterFactory,
    internal val smartConfigFactory: SmartConfigFactory,
) {
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
    var kafkaServers = "localhost:9092"

    @Option(
        names = ["--config-topic-name"],
        description = ["The config topic name (default: \${DEFAULT-VALUE})"]
    )
    private var configTopicName = "ConfigTopic"

    @Option(
        names = ["--topic-prefix"],
        description = ["The topic prefix (default: \${DEFAULT-VALUE})"]
    )
    var topicPrefix = "p2p"

    private val kafkaNodeConfiguration: Config by lazy {
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
            )
    }

    fun createWriter(): ConfigWriter =
        configWriterFactory.createWriter(
            configTopicName,
            smartConfigFactory.create(kafkaNodeConfiguration)
        )
}
