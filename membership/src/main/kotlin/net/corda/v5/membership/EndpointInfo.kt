package net.corda.v5.membership

import net.corda.v5.base.annotations.CordaSerializable

/**
 * Information about node's endpoint.
 *
 * @property url Endpoint base URL.
 * @property protocolVersion Version of end-to-end authentication protocol. If multiple versions are supported, the same URL should be listed multiple times.
 */
@CordaSerializable
interface EndpointInfo {
    companion object {
        const val DEFAULT_PROTOCOL_VERSION = 1
    }

    val url: String
    val protocolVersion: Int
}