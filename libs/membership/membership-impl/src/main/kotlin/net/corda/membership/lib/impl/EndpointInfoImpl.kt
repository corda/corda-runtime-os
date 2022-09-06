package net.corda.membership.lib.impl

import net.corda.membership.lib.impl.EndpointInfoImpl.Companion.DEFAULT_PROTOCOL_VERSION
import net.corda.utilities.NetworkHostAndPort
import net.corda.v5.membership.EndpointInfo

data class EndpointInfoImpl(
    override val url: String,
    override val protocolVersion: Int = DEFAULT_PROTOCOL_VERSION
) : EndpointInfo {
    internal companion object {
        const val DEFAULT_PROTOCOL_VERSION = 1
    }
}

fun NetworkHostAndPort.toEndpointInfo(protocolVersion: Int = DEFAULT_PROTOCOL_VERSION) =
    EndpointInfoImpl("https://${host}:${port}", protocolVersion)