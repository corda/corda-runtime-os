package net.corda.p2p.config.publisher

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.publish.CordaConfigurationKey
import net.corda.libs.configuration.publish.CordaConfigurationVersion
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration
import net.corda.p2p.crypto.ProtocolMode
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.TypeConversionException

@Command(
    name = "link-manager",
    aliases = ["lm", "linkmanager", "link_manager"],
    description = ["Publish the P2P Link Manager configuration"],
    showAtFileInUsageHelp = true,
    showDefaultValues = true,
)
class LinkManagerConfiguration : ConfigProducer() {
    @Option(
        names = ["--locallyHostedIdentity"],
        description = ["Local hosted identity (in the form of <x500Name>:<groupId>)"],
        required = true,
    )
    lateinit var locallyHostedIdentity: List<String>

    @Option(
        names = ["--maxMessageSize"],
        description = ["The maximal message size in bytes"]
    )
    var maxMessageSize = 1_000_000

    @Option(
        names = ["--protocolMode"],
        description = ["Supported protocol mode (out of: \${COMPLETION-CANDIDATES})"]
    )
    var protocolModes: List<ProtocolMode> = listOf(ProtocolMode.AUTHENTICATED_ENCRYPTION)

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

    @Option(
        names = ["--sessionsPerCounterparties"],
        description = ["The number of sessions between two peers"]
    )
    var sessionsPerCounterparties = 4L

    override val configuration by lazy {
        val locallyHostedIdentities = locallyHostedIdentity.map {
            it.split(":")
        }.onEach {
            if (it.size != 2) {
                throw TypeConversionException("locallyHostedIdentity must have the format <x500Name>:<groupId>")
            }
        }.map {
            it[0] to it[1]
        }.map {
            mapOf(
                "x500Name" to it.first,
                "groupId" to it.second
            )
        }
        ConfigFactory.empty()
            .withValue(
                LinkManagerConfiguration.LOCALLY_HOSTED_IDENTITIES_KEY,
                ConfigValueFactory.fromAnyRef(locallyHostedIdentities)
            )
            .withValue(
                LinkManagerConfiguration.MAX_MESSAGE_SIZE_KEY,
                ConfigValueFactory.fromAnyRef(maxMessageSize)
            )
            .withValue(
                LinkManagerConfiguration.PROTOCOL_MODE_KEY ,
                ConfigValueFactory.fromAnyRef(protocolModes.map { it.toString() })
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
            .withValue(
                LinkManagerConfiguration.SESSIONS_PER_COUNTERPARTIES_KEY,
                ConfigValueFactory.fromAnyRef(sessionsPerCounterparties)
            )
    }

    override val key = CordaConfigurationKey(
        "p2p-link-manager",
        CordaConfigurationVersion(LinkManagerConfiguration.PACKAGE_NAME , 1, 0),
        CordaConfigurationVersion(LinkManagerConfiguration.COMPONENT_NAME, 1, 0)
    )
}
