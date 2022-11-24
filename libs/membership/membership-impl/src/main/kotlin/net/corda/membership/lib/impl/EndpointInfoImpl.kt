package net.corda.membership.lib.impl

import net.corda.membership.lib.EndpointInfoFactory
import net.corda.v5.membership.EndpointInfo

data class EndpointInfoImpl(
    override val url: String,
    override val protocolVersion: Int = EndpointInfoFactory.DEFAULT_PROTOCOL_VERSION
) : EndpointInfo
