@file:Suppress("DEPRECATION")

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
        names = ["--messageReplayPeriodMilliSecs"],
        description = ["message replay period in milliseconds"]
    )
    var messageReplayPeriodMilliSecs = 2_000L

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
                ConfigValueFactory.fromAnyRef(messageReplayPeriodMilliSecs)
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
        CordaConfigurationVersion(LinkManagerConfiguration.PACKAGE_NAME , 1, 0),
        CordaConfigurationVersion(LinkManagerConfiguration.COMPONENT_NAME, 1, 0)
    )
}
