package net.corda.membership.impl

import net.corda.v5.base.util.NetworkHostAndPort
import net.corda.v5.membership.EndpointInfo

data class EndpointInfoImpl(override val url: String, override val protocolVersion: Int) : EndpointInfo

fun NetworkHostAndPort.toEndpointInfo(protocolVersion: Int) =
    EndpointInfoImpl("https://${host}:${port}", protocolVersion)