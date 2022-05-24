package net.corda.p2p.setup

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import kotlin.random.Random
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.BootConfig.TOPIC_PREFIX
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE
import net.corda.schema.configuration.MessagingConfig.Bus.KAFKA_BOOTSTRAP_SERVERS
import net.corda.schema.configuration.MessagingConfig.Bus.KAFKA_PRODUCER_CLIENT_ID
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

    @Option(
        names = ["-k", "--kafka-servers"],
        description = ["A comma-separated list of addresses of Kafka brokers"]
    )
    private var kafkaServers = System.getenv("KAFKA_SERVERS") ?: "localhost:9092"

    internal fun nodeConfiguration(): SmartConfig {
        val secretsConfig = ConfigFactory.empty()
        return SmartConfigFactory.create(secretsConfig).create(
            ConfigFactory.empty()
                .withValue(
                    KAFKA_BOOTSTRAP_SERVERS,
                    ConfigValueFactory.fromAnyRef(kafkaServers)
                )
                .withValue(BUS_TYPE, ConfigValueFactory.fromAnyRef("KAFKA"))
                .withValue(KAFKA_PRODUCER_CLIENT_ID, ConfigValueFactory.fromAnyRef("p2p-setup"))
                .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(""))
                .withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(Random.nextInt()))
        )
    }
}
