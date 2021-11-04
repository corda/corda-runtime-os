package net.corda.p2p.config.publisher

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.write.CordaConfigurationKey
import net.corda.libs.configuration.write.CordaConfigurationVersion
import net.corda.p2p.crypto.ProtocolMode
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.TypeConversionException

@Command(
    name = "link-manager",
    aliases = ["lm", "linkmanager", "link_manager"],
    description = ["Publish the P2P Link Manager configuration"]
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
        description = ["The maximal message size (default: \${DEFAULT-VALUE})"]
    )
    var maxMessageSize = 500

    @Option(
        names = ["--protocolMode"],
        description = ["Supported protocol mode (out of: \${COMPLETION-CANDIDATES}; default: \${DEFAULT-VALUE})"]
    )
    var protocolModes: List<ProtocolMode> = listOf(ProtocolMode.AUTHENTICATED_ENCRYPTION)

    @Option(
        names = ["--messageReplayPeriodSecs"],
        description = ["message replay period in seconds (default: \${DEFAULT-VALUE})"]
    )
    var messageReplayPeriodSecs = 2L

    @Option(
        names = ["--heartbeatMessagePeriodMilliSecs"],
        description = ["Heartbeat message period in milli seconds (default: \${DEFAULT-VALUE})"]
    )
    var heartbeatMessagePeriodMilliSecs = 2_000L

    @Option(
        names = ["--sessionTimeoutMilliSecs"],
        description = ["Session timeout in milliseconds (default: \${DEFAULT-VALUE})"]
    )
    var sessionTimeoutMilliSecs = 10_000L

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
                "locallyHostedIdentities",
                ConfigValueFactory.fromAnyRef(locallyHostedIdentities)
            )
            .withValue(
                "maxMessageSize",
                ConfigValueFactory.fromAnyRef(maxMessageSize)
            )
            .withValue(
                "protocolMode",
                ConfigValueFactory.fromAnyRef(protocolModes.map { it.toString() })
            )
            .withValue(
                "messageReplayPeriod",
                ConfigValueFactory.fromAnyRef(messageReplayPeriodSecs)
            )
            .withValue(
                "heartbeatMessagePeriod",
                ConfigValueFactory.fromAnyRef(heartbeatMessagePeriodMilliSecs)
            )
            .withValue(
                "sessionTimeout",
                ConfigValueFactory.fromAnyRef(sessionTimeoutMilliSecs)
            )
    }

    override val key = CordaConfigurationKey(
        "p2p-link-manager",
        CordaConfigurationVersion("p2p", 1, 0),
        CordaConfigurationVersion("link-manager", 1, 0)
    )
}
