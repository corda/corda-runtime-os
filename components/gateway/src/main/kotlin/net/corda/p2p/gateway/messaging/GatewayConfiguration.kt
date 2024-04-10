package net.corda.p2p.gateway.messaging

import com.typesafe.config.Config
import java.time.Duration

internal data class GatewayConfiguration(
    /**
     * The gateway servers configurations
     */
    val serversConfiguration: Collection<GatewayServerConfiguration>,
    /**
     * TLS configuration used for establishing the HTTPS connections
     */
    val sslConfig: SslConfiguration,
    /**
     * Max request size acceptable by the gateway (in bytes).
     */
    val maxRequestSize: Long,
    /**
     * Configuration properties used when initiating connections to other Gateways
     */
    val connectionConfig: ConnectionConfiguration = ConnectionConfiguration(),
)

internal data class GatewayServerConfiguration(
    /**
     * Host name or IP address used when binding the HTTP server
     */
    val hostAddress: String,
    /**
     * Port number used when binding the HTTP server
     */
    val hostPort: Int,
    /**
     * The URL paths the gateway HTTP server will listen to for requests.
     */
    val urlPaths: Collection<String>,
) {
    constructor(
        hostAddress: String,
        hostPort: Int,
        urlPath: String,
    ) : this(
        hostAddress,
        hostPort,
        setOf(urlPath),
    )
}

data class ConnectionConfiguration(
    /**
     * Maximum size of the connection cache
     */
    val maxClientConnections: Long = 100L,
    /**
     * Time after which a connection request will fail
     */
    val acquireTimeout: Duration = Duration.ofSeconds(10),
    /**
     * Time after which an inactive connection in the pool will be released (closed)
     */
    val connectionIdleTimeout: Duration = Duration.ofMinutes(1),

    /**
     * Time after which a message delivery is considered failed
     */
    val responseTimeout: Duration = Duration.ofSeconds(3),

    /**
     * Time after which a message is retried, when previously failed.
     */
    val retryDelay: Duration = Duration.ofSeconds(2),

    /**
     * initial duration to wait before trying to reconnect disconnection.
     */
    val initialReconnectionDelay: Duration = Duration.ofSeconds(1),

    /**
     * The maximum duration to wait for reconnection.
     */
    val maxReconnectionDelay: Duration = Duration.ofSeconds(10),
)

internal fun Config.toGatewayConfiguration(): GatewayConfiguration {
    val connectionConfig = if (this.hasPath("connectionConfig")) {
        this.getConfig("connectionConfig").toConnectionConfig()
    } else {
        ConnectionConfiguration()
    }
    return GatewayConfiguration(
        serversConfiguration = this.getConfigList("serversConfiguration").map {
            it.toServerConfiguration()
        },
        sslConfig = this.getConfig("sslConfig").toSslConfiguration(),
        maxRequestSize = this.getLong("maxRequestSize"),
        connectionConfig = connectionConfig,
    )
}
internal fun Config.toServerConfiguration(): GatewayServerConfiguration {
    return GatewayServerConfiguration(
        hostAddress = this.getString("hostAddress"),
        hostPort = this.getInt("hostPort"),
        urlPath = this.getString("urlPath"),
    )
}
private fun Config.toConnectionConfig(): ConnectionConfiguration {
    return ConnectionConfiguration(
        maxClientConnections = this.getLong("maxClientConnections"),
        acquireTimeout = Duration.ofSeconds(this.getLong("acquireTimeout")),
        connectionIdleTimeout = Duration.ofSeconds(this.getLong("connectionIdleTimeout")),
        responseTimeout = Duration.ofMillis(this.getLong("responseTimeout")),
        retryDelay = Duration.ofMillis(this.getLong("retryDelay")),
        initialReconnectionDelay = Duration.ofSeconds(this.getLong("initialReconnectionDelay")),
        maxReconnectionDelay = Duration.ofSeconds(this.getLong("maxReconnectionDelay")),
    )
}
