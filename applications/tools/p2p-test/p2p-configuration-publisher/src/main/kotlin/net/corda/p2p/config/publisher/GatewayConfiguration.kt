package net.corda.p2p.config.publisher

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.publish.CordaConfigurationKey
import net.corda.libs.configuration.publish.CordaConfigurationVersion
import net.corda.p2p.gateway.messaging.RevocationConfigMode
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.time.Duration

@Command(
    name = "gateway",
    description = ["Publish the P2P gateway configuration"],
    showAtFileInUsageHelp = true,
    showDefaultValues = true,
)
class GatewayConfiguration : ConfigProducer() {
    @Option(
        names = ["--hostAddress"],
        description = ["The host name or IP address where the HTTP server will bind"]
    )
    var hostAddress: String = "0.0.0.0"

    @Option(
        names = ["--port"],
        description = ["The HTTP port"],
        required = true
    )
    var port: Int = 0

    @Option(
        names = ["--revocationCheck"],
        description = ["Revocation Check mode (one of: \${COMPLETION-CANDIDATES})"]
    )
    var revocationCheck = RevocationConfigMode.OFF

    @Option(
        names = ["--maxClientConnections"],
        description = ["The maximal number of client connections"]
    )
    var maxClientConnections = 100L

    @Option(
        names = ["--acquireTimeoutSec"],
        description = ["The client connection acquire timeout in seconds"]
    )
    var acquireTimeoutSec = 10L

    @Option(
        names = ["--connectionIdleTimeoutSec"],
        description = ["The amount of time to keep inactive client connection before closing it in seconds"]
    )
    var connectionIdleTimeoutSec = 60L

    @Option(
        names = ["--connectionInitialReconnectionDelaySec"],
        description = ["The initial duration (in seconds) to wait before trying to reconnect"]
    )
    var connectionInitialReconnectionDelaySec = 1L

    @Option(
        names = ["--connectionMaximalReconnectionDelaySec"],
        description = ["The maximal duration (in seconds) to delay before trying to reconnect"]
    )
    var connectionMaximalReconnectionDelaySec = 16L

    @Option(
        names = ["--responseTimeoutMilliSecs"],
        description = ["Time after which a message delivery is considered failed in milliseconds"]
    )
    var responseTimeoutMilliSecs = 1_000L

    @Option(
        names = ["--retryDelayMilliSecs"],
        description = ["Time after which a message is retried, when previously failed in milliseconds"]
    )
    var retryDelayMilliSecs = 1_000L

    @Option(
        names = ["--nameResolverType"],
        description = ["Type of name resolver. Either FIRST_IP_ALWAYS or ROUND_ROBIN."]
    )
    var nameResolverType = "FIRST_IP_ALWAYS"

    override val configuration by lazy {
        ConfigFactory.empty()
            .withValue(
                "hostAddress",
                ConfigValueFactory.fromAnyRef(hostAddress)
            )
            .withValue(
                "hostPort",
                ConfigValueFactory.fromAnyRef(port)
            )
            .withValue(
                "sslConfig.revocationCheck.mode",
                ConfigValueFactory.fromAnyRef(revocationCheck.toString())
            )
            .withValue(
                "connectionConfig.maxClientConnections",
                ConfigValueFactory.fromAnyRef(maxClientConnections)
            )
            .withValue(
                "connectionConfig.acquireTimeout",
                ConfigValueFactory.fromAnyRef(Duration.ofSeconds(acquireTimeoutSec))
            )
            .withValue(
                "connectionConfig.connectionIdleTimeout",
                ConfigValueFactory.fromAnyRef(Duration.ofSeconds(connectionIdleTimeoutSec))
            )
            .withValue(
                "connectionConfig.responseTimeout",
                ConfigValueFactory.fromAnyRef(Duration.ofMillis(responseTimeoutMilliSecs))
            )
            .withValue(
                "connectionConfig.retryDelay",
                ConfigValueFactory.fromAnyRef(Duration.ofMillis(retryDelayMilliSecs))
            )
            .withValue(
                "connectionConfig.initialReconnectionDelay",
                ConfigValueFactory.fromAnyRef(Duration.ofSeconds(connectionInitialReconnectionDelaySec))
            )
            .withValue(
                "connectionConfig.maximalReconnectionDelay",
                ConfigValueFactory.fromAnyRef(Duration.ofSeconds(connectionMaximalReconnectionDelaySec))
            )
            .withValue(
                "connectionConfig.nameResolverType",
                ConfigValueFactory.fromAnyRef(nameResolverType)
            )
    }

    override val key = CordaConfigurationKey(
        "p2p-gateway",
        CordaConfigurationVersion("p2p", 1, 0),
        CordaConfigurationVersion("gateway", 1, 0)
    )
}
