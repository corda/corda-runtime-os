package net.corda.messaging.api.subscription.config

/**
 * HttpRPCConfig
 *
 * @property endpoint the endpoint to register eg '/test-endpoint-1'
 */
data class HttpRPCConfig(
    val endpoint: String,
)