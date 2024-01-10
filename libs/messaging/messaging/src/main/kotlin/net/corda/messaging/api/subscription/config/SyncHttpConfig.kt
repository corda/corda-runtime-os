package net.corda.messaging.api.subscription.config

/**
 * Config for a synchronous HTTP subscription.
 *
 * @property name of the subscription
 * @property endpoint the endpoint path to register. See
 */
data class SyncHttpConfig(
    val name: String,
    val endpoint: String,
)