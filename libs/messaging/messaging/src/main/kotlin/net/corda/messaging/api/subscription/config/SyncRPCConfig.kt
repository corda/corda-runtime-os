package net.corda.messaging.api.subscription.config

/**
 * SyncRPCConfig
 *
 * @property endpoint the endpoint to register eg '/test-endpoint-1'
 */
data class SyncRPCConfig(
    val endpoint: String,
)