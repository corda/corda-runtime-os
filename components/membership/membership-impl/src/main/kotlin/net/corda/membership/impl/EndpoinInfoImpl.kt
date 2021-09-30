package net.corda.membership.impl

import net.corda.v5.application.node.EndpointInfo
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.util.NetworkHostAndPort

@CordaSerializable
data class EndpointInfoImpl(override val url: String, override val protocolVersion: Int) : EndpointInfo

fun NetworkHostAndPort.toEndpointInfo(protocolVersion: Int) =
    EndpointInfoImpl("https://${host}:${port}", protocolVersion)