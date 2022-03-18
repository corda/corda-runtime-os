package net.corda.p2p.gateway.messaging

import com.typesafe.config.Config
import java.time.Duration

data class GatewayConfiguration(
    /**
     * Host name or IP address used when binding the HTTP server
     */
    val hostAddress: String,
    /**
     * Port number used when binding the HTTP server
     */
    val hostPort: Int,
    /**
     * TLS configuration used for establishing the HTTPS connections
     */
    val sslConfig: SslConfiguration,
    /**
     * Configuration properties used when initiating connections to other Gateways
     */
    val connectionConfig: ConnectionConfiguration = ConnectionConfiguration(),
)

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
    val responseTimeout: Duration = Duration.ofSeconds(1),

    /**
     * Time after which a message is retried, when previously failed.
     */
    val retryDelay: Duration = Duration.ofSeconds(1),

    /**
     * initial duration to wait before trying to reconnect disconnection.
     */
    val initialReconnectionDelay: Duration = Duration.ofSeconds(1),

    /**
     * The maximal duration to wait for reconnection.
     */
    val maximalReconnectionDelay: Duration = Duration.ofSeconds(10),

    /**
     * The type of name resolution.
     */
    val nameResolverType: NameResolverType = NameResolverType.FIRST_IP_ALWAYS,
)

/**
 * Types on name resolution (when there is more than one IP address for a given name).
 */
enum class NameResolverType {
    /**
     * Always use the first IP address
     */
    FIRST_IP_ALWAYS,

    /**
     * Choose a random IP address.
     */
    ROUND_ROBIN,

    /**
     * Choose a random IP address from the configuration.
     */
    OVERWRITE_RESOLVER,
}

internal fun Config.toGatewayConfiguration(): GatewayConfiguration {
    val connectionConfig = if (this.hasPath("connectionConfig")) {
        this.getConfig("connectionConfig").toConnectionConfig()
    } else {
        ConnectionConfiguration()
    }
    return GatewayConfiguration(
        hostAddress = this.getString("hostAddress"),
        hostPort = this.getInt("hostPort"),
        sslConfig = this.getConfig("sslConfig").toSslConfiguration(),
        connectionConfig = connectionConfig,
    )
}
private fun Config.toConnectionConfig(): ConnectionConfiguration {
    return ConnectionConfiguration(
        maxClientConnections = this.getLong("maxClientConnections"),
        acquireTimeout = this.getDuration("acquireTimeout"),
        connectionIdleTimeout = this.getDuration("connectionIdleTimeout"),
        responseTimeout = this.getDuration("responseTimeout"),
        retryDelay = this.getDuration("retryDelay"),
        initialReconnectionDelay = this.getDuration("initialReconnectionDelay"),
        maximalReconnectionDelay = this.getDuration("maximalReconnectionDelay"),
        nameResolverType = this.getEnum(NameResolverType::class.java, "nameResolverType")
    )
}
