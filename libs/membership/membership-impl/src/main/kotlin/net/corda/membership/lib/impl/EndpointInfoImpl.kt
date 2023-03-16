package net.corda.membership.lib.impl

import net.corda.membership.lib.EndpointInfoFactory
import net.corda.v5.membership.EndpointInfo

data class EndpointInfoImpl(
    private val url: String,
    private val protocolVersion: Int = EndpointInfoFactory.DEFAULT_PROTOCOL_VERSION
) : EndpointInfo {
    override fun getUrl() = url
    override fun getProtocolVersion() = protocolVersion
}
