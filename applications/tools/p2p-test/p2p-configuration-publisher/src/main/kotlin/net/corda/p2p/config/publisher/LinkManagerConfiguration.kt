package net.corda.p2p.config.publisher

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.publish.CordaConfigurationKey
import net.corda.libs.configuration.publish.CordaConfigurationVersion
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "link-manager",
    aliases = ["lm", "linkmanager", "link_manager"],
    description = ["Publish the P2P Link Manager configuration"],
    showAtFileInUsageHelp = true,
    showDefaultValues = true,
)
class LinkManagerConfiguration : ConfigProducer() {
    @Option(
        names = ["--maxMessageSize"],
        description = ["The maximal message size in bytes"]
    )
    var maxMessageSize = 1_000_000

    @Option(
        names = ["--messageReplayPeriodBaseMilliSecs"],
        description = ["message replay period base in milliseconds"]
    )
    var messageReplayPeriodBaseMilliSecs = 2_000L

    @Option(
        names = ["--messageReplayPeriodCutOffMilliSecs"],
        description = ["message replay period cut off in milliseconds"]
    )
    var messageReplayPeriodCutoffMilliSecs = 10_000L

    @Option(
        names = ["--maxReplayingMessages"],
        description = ["the maximum number of replaying messages between two peers"]
    )
    var maxReplayingMessages = 100

    @Option(
        names = ["--heartbeatMessagePeriodMilliSecs"],
        description = ["Heartbeat message period in milli seconds"]
    )
    var heartbeatMessagePeriodMilliSecs = 2_000L

    @Option(
        names = ["--sessionTimeoutMilliSecs"],
        description = ["Session timeout in milliseconds"]
    )
    var sessionTimeoutMilliSecs = 10_000L

    override val configuration by lazy {
        ConfigFactory.empty()
            .withValue(
                LinkManagerConfiguration.MAX_MESSAGE_SIZE_KEY,
                ConfigValueFactory.fromAnyRef(maxMessageSize)
            )
            .withValue(
                LinkManagerConfiguration.MESSAGE_REPLAY_KEY_PREFIX + LinkManagerConfiguration.BASE_REPLAY_PERIOD_KEY_POSTFIX,
                ConfigValueFactory.fromAnyRef(messageReplayPeriodBaseMilliSecs)
            )
            .withValue(
                LinkManagerConfiguration.MESSAGE_REPLAY_KEY_PREFIX + LinkManagerConfiguration.CUTOFF_REPLAY_KEY_POSTFIX,
                ConfigValueFactory.fromAnyRef(messageReplayPeriodCutoffMilliSecs)
            )
            .withValue(
                LinkManagerConfiguration.MESSAGE_REPLAY_KEY_PREFIX + LinkManagerConfiguration.MAX_REPLAYING_MESSAGES_PER_PEER_POSTFIX,
                ConfigValueFactory.fromAnyRef(maxReplayingMessages)
            )
            .withValue(
                LinkManagerConfiguration.HEARTBEAT_MESSAGE_PERIOD_KEY,
                ConfigValueFactory.fromAnyRef(heartbeatMessagePeriodMilliSecs)
            )
            .withValue(
                LinkManagerConfiguration.SESSION_TIMEOUT_KEY,
                ConfigValueFactory.fromAnyRef(sessionTimeoutMilliSecs)
            )
    }

    override val key = CordaConfigurationKey(
        "p2p-link-manager",
        CordaConfigurationVersion(LinkManagerConfiguration.PACKAGE_NAME, 1, 0),
        CordaConfigurationVersion(LinkManagerConfiguration.COMPONENT_NAME, 1, 0)
    )
}
