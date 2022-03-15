package net.corda.p2p.gateway.messaging.http

import io.netty.resolver.AddressResolver
import io.netty.resolver.AddressResolverGroup
import io.netty.resolver.DefaultAddressResolverGroup
import io.netty.resolver.DefaultNameResolver
import io.netty.resolver.RoundRobinInetAddressResolver
import io.netty.util.concurrent.EventExecutor
import net.corda.p2p.gateway.messaging.NameResolverType
import java.net.InetSocketAddress

internal fun NameResolverType.toResolver() = when (this) {
    NameResolverType.FIRST_IP_ALWAYS -> DefaultAddressResolverGroup.INSTANCE
    NameResolverType.ROUND_ROBIN -> RoundRobinNameResolver()
}

internal class RoundRobinNameResolver : AddressResolverGroup<InetSocketAddress>() {
    override fun newResolver(executor: EventExecutor?): AddressResolver<InetSocketAddress> {
        val defaultNameResolver = DefaultNameResolver(executor)
        return RoundRobinInetAddressResolver(executor, defaultNameResolver)
            .asAddressResolver()
    }
}
