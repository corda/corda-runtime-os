package net.corda.p2p.setup

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    header = ["P2P Setup"],
    name = "p2p-setup",
    description = ["Setup P2P components"],
    subcommands = [
        GatewayConfiguration::class,
        LinkManagerConfiguration::class,
        AddGroup::class,
        AddMember::class,
        AddIdentity::class,
        AddKeyPair::class,
        RemoveGroup::class,
        RemoveMember::class,
        RemoveIdentity::class,
        Apply::class,
    ],
    mixinStandardHelpOptions = true,
    showDefaultValues = true,
    showAtFileInUsageHelp = true,
    subcommandsRepeatable = true,
    usageHelpAutoWidth = true,
)
class Command {
    private companion object {
        private const val KAFKA_COMMON_BOOTSTRAP_SERVER = "messaging.kafka.common.bootstrap.servers"
        private const val PRODUCER_CLIENT_ID = "messaging.kafka.producer.client.id"
        private const val TOPIC_PREFIX = "messaging.topic.prefix"
    }

    @Option(
        names = ["-k", "--kafka-servers"],
        description = ["The kafka servers"]
    )
    private var kafkaServers = System.getenv("KAFKA_SERVERS") ?: "localhost:9092"

    internal fun nodeConfiguration(): SmartConfig {
        val secretsConfig = ConfigFactory.empty()
        return SmartConfigFactory.create(secretsConfig).create(
            ConfigFactory.empty()
                .withValue(
                    KAFKA_COMMON_BOOTSTRAP_SERVER,
                    ConfigValueFactory.fromAnyRef(kafkaServers)
                )
                .withValue(PRODUCER_CLIENT_ID, ConfigValueFactory.fromAnyRef("p2p-setup"))
                .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(""))
        )
    }
}
