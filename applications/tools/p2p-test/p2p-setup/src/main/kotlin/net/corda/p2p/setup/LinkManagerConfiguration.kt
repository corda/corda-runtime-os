package net.corda.p2p.setup

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.data.config.Configuration
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.ReplayAlgorithm
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.util.concurrent.Callable

@Command(
    name = "config-link-manager",
    aliases = ["config-lm", "config-linkmanager", "config-link_manager"],
    description = ["Configure the P2P Link Manager"],
    showAtFileInUsageHelp = true,
    showDefaultValues = true,
    usageHelpAutoWidth = true,
)
class LinkManagerConfiguration : Callable<Collection<Record<String, Configuration>>> {
    @Option(
        names = ["--topic"],
        description = ["The configuration topic"]
    )
    var topic: String = Schemas.Config.CONFIG_TOPIC

    @Option(
        names = ["--maxMessageSize"],
        description = ["The maximal message size in bytes"]
    )
    var maxMessageSize = 1_000_000

    @Option(
        names = ["--replayAlgorithm"],
        description = ["The algorithm used to schedule messages for replay. One of: \${COMPLETION-CANDIDATES}."]
    )
    var replayAlgorithm = ReplayAlgorithm.Constant

    @Option(
        names = ["--messageReplayPeriodMilliSecs"],
        description = ["The message replay period in milliseconds. Only used with Constant replayAlgorithm."]
    )
    var messageReplayPeriodMilliSecs = 2_000L

    @Option(
        names = ["--messageReplayPeriodBaseMilliSecs"],
        description = ["The message replay period base in milliseconds. Only used with ExponentialBackoff replayAlgorithm."]
    )
    var messageReplayPeriodBaseMilliSecs = 2_000L

    @Option(
        names = ["--messageReplayPeriodCutOffMilliSecs"],
        description = ["The message replay period cut off in milliseconds. Only used with ExponentialBackoff replayAlgorithm."]
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

    @Option(
        names = ["--sessionsPerPeer"],
        description = ["The number of sessions between two peers"]
    )
    var sessionsPerPeer = 4L

    override fun call(): Collection<Record<String, Configuration>> {
        val baseConfiguration = ConfigFactory.empty()
            .withValue(
                LinkManagerConfiguration.MAX_MESSAGE_SIZE_KEY,
                ConfigValueFactory.fromAnyRef(maxMessageSize)
            )
            .withValue(
                LinkManagerConfiguration.MAX_REPLAYING_MESSAGES_PER_PEER,
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
            .withValue(
                LinkManagerConfiguration.SESSIONS_PER_PEER_KEY,
                ConfigValueFactory.fromAnyRef(sessionsPerPeer)
            )

        val replayAlgorithmInnerConfig = when (replayAlgorithm) {
            ReplayAlgorithm.Constant -> {
                ConfigFactory.empty().withValue(
                    LinkManagerConfiguration.REPLAY_PERIOD_KEY,
                    ConfigValueFactory.fromAnyRef(messageReplayPeriodMilliSecs)
                )
            }
            ReplayAlgorithm.ExponentialBackoff -> {
                ConfigFactory.empty().withValue(
                    LinkManagerConfiguration.BASE_REPLAY_PERIOD_KEY,
                    ConfigValueFactory.fromAnyRef(messageReplayPeriodBaseMilliSecs)
                ).withValue(
                    LinkManagerConfiguration.REPLAY_PERIOD_CUTOFF_KEY,
                    ConfigValueFactory.fromAnyRef(messageReplayPeriodCutoffMilliSecs)
                )
            }
        }
        val configuration = baseConfiguration.withValue(replayAlgorithm.configKeyName(), replayAlgorithmInnerConfig.root())

        return listOf(
            configuration.toConfigurationRecord(
                LinkManagerConfiguration.PACKAGE_NAME,
                LinkManagerConfiguration.COMPONENT_NAME,
                topic
            )
        )
    }
}
