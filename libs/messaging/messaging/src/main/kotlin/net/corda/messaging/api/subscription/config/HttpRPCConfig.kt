package net.corda.messaging.api.subscription.config

/**
 * HttpRPCConfig
 *
 * @property name Subscription name
 * @property endpoint the endpoint to register eg '/test-endpoint-1'
 */
data class HttpRPCConfig(
    val name: String,
    val endpoint: String,
)