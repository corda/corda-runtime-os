package net.corda.configuration.rpcops.impl.v1.types

/**
 * The data object received via HTTP in response to a request to update cluster configuration.
 *
 * @property success Indicates whether the request was successful.
 */
internal data class HTTPUpdateConfigResponse(
    val success: Boolean
)