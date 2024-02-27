package net.corda.membership.lib

import net.corda.v5.membership.EndpointInfo

/**
 * EndpointInfoFactory is a factory for building [EndpointInfo] objects. [EndpointInfo] contains information about a virtual
 * node's endpoint, for example, a virtual node's peer-to-peer gateway endpoint.
 */
interface EndpointInfoFactory {
    companion object {
        const val DEFAULT_PROTOCOL_VERSION = 1
    }

    /**
     * The [create] method allows you to create an instance of [EndpointInfo] using the specified URL string and
     * protocol version.
     *
     * Example usage:
     * ```
     * factory.create("https://corda5.r3.com:10001", 10)
     *
     * factory.create("https://corda5.r3.com:10002")
     * ```
     *
     * @param url The complete URL of the virtual node endpoint.
     * @param protocolVersion The version of end-to-end authentication protocol. If multiple versions are supported,
     * multiple instances of [EndpointInfo] can be created, each using a different protocol version. Defaults to
     * [DEFAULT_PROTOCOL_VERSION].
     */
    fun create(
        url: String,
        protocolVersion: Int = DEFAULT_PROTOCOL_VERSION
    ): EndpointInfo
}
