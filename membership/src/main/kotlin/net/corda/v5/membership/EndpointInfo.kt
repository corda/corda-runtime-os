package net.corda.v5.membership

import net.corda.v5.base.annotations.CordaSerializable

/**
 * Information about a virtual node's endpoint (e.g. a virtual node's peer-to-peer gateway endpoint).
 */
@CordaSerializable
interface EndpointInfo {
    /**
     * Full virtual node endpoint URL.
     */
    val url: String

    /**
     * Version of end-to-end authentication protocol. If multiple versions are supported, multiple instances of
     * [EndpointInfo] can be created, each using a different protocol version.
     */
    val protocolVersion: Int
}