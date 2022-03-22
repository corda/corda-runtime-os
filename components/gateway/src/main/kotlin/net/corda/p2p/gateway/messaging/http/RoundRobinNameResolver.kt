package net.corda.p2p.gateway.messaging.http

import io.netty.resolver.AddressResolver
import io.netty.resolver.AddressResolverGroup
import io.netty.resolver.DefaultNameResolver
import io.netty.resolver.RoundRobinInetAddressResolver
import io.netty.util.concurrent.EventExecutor
import java.net.InetSocketAddress

internal class RoundRobinNameResolver : AddressResolverGroup<InetSocketAddress>() {
    override fun newResolver(executor: EventExecutor): AddressResolver<InetSocketAddress> {
        val defaultNameResolver = DefaultNameResolver(executor)
        return RoundRobinInetAddressResolver(executor, defaultNameResolver)
            .asAddressResolver()
    }
}
