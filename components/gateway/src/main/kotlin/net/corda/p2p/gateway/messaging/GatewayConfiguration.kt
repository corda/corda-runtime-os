package net.corda.p2p.gateway.messaging

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
     * Value represents the maximum size of the messages this Gateway will accept or send
     */
    val maxMessageSize: Int = 1024 * 1024,
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
     * Time in milliseconds after which a connection request will fail
     */
    val acquireTimeout: Long = 10000L,
    /**
     * Time in milliseconds after which an inactive connection in the pool will be released (closed)
     */
    val connectionIdleTimeout: Long = 60000L,
    /**
     * Time in milliseconds after which a message delivery is considered failed
     */
    val responseTimeout: Long = 1000L,

    val retryDelay: Long = 5000L
)
