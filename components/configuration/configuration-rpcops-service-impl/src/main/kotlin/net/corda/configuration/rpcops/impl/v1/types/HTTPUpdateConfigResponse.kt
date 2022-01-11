package net.corda.configuration.rpcops.impl.v1.types

/**
 * The data object received via HTTP in response to a request to update cluster configuration.
 *
 * @property config The updated configuration.
 */
internal data class HTTPUpdateConfigResponse(
    val config: String
)