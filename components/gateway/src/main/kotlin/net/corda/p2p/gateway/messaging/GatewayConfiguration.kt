package net.corda.p2p.gateway.messaging

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
    /**
     * Determines whether HTTP pipeline logging is enabled or not. Should only be turned on when debugging
     */
    val traceLogging: Boolean = false
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

    val retryDelay: Duration = Duration.ofSeconds(5)
)
