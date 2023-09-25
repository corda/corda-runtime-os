package net.corda.messaging.api.subscription.config

/**
 * SyncRPCConfig
 *
 * @property name Subscription name
 * @property endpoint the endpoint to register eg '/test-endpoint-1'
 */
data class SyncRPCConfig(
    val name: String,
    val endpoint: String,
)