package net.corda.p2p.setup

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.data.config.Configuration
import net.corda.messaging.api.records.Record
import net.corda.p2p.gateway.messaging.RevocationConfigMode
import net.corda.schema.Schemas
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.time.Duration
import java.util.concurrent.Callable

@Command(
    name = "config-gateway",
    aliases = ["config-gw"],
    description = ["Configure the P2P gateway"],
    showAtFileInUsageHelp = true,
    showDefaultValues = true,
    usageHelpAutoWidth = true,
)
class GatewayConfiguration : Callable<Collection<Record<String, Configuration>>> {
    @Option(
        names = ["--topic"],
        description = ["The configuration topic"]
    )
    var topic: String = Schemas.Config.CONFIG_TOPIC

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

    override fun call(): Collection<Record<String, Configuration>> {
        val configuration = ConfigFactory.empty()
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

        return listOf(
            configuration.toConfigurationRecord(
                "p2p",
                "gateway",
                topic
            )
        )
    }
}
