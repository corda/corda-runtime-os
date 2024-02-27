package net.corda.membership.lib.impl

import net.corda.membership.lib.EndpointInfoFactory
import net.corda.utilities.NetworkHostAndPort
import org.osgi.service.component.annotations.Component

@Component(service = [EndpointInfoFactory::class])
class EndpointInfoFactoryImpl : EndpointInfoFactory {
    override fun create(
        url: String,
        protocolVersion: Int
    ) = EndpointInfoImpl(url, protocolVersion)
}

fun NetworkHostAndPort.toEndpointInfo(protocolVersion: Int = EndpointInfoFactory.DEFAULT_PROTOCOL_VERSION) =
    EndpointInfoImpl("https://$host:$port", protocolVersion)
