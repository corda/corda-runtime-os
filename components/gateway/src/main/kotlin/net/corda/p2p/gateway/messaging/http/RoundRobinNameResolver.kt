package net.corda.p2p.gateway.messaging.http

import io.netty.resolver.AddressResolver
import io.netty.resolver.AddressResolverGroup
import io.netty.resolver.DefaultAddressResolverGroup
import io.netty.resolver.DefaultNameResolver
import io.netty.resolver.InetNameResolver
import io.netty.resolver.RoundRobinInetAddressResolver
import io.netty.util.concurrent.EventExecutor
import io.netty.util.concurrent.Promise
import io.netty.util.internal.SocketUtils
import net.corda.p2p.gateway.messaging.NameResolverType
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.UnknownHostException

internal fun NameResolverType.toResolver(hostMap: HostsMap?) = when (this) {
    NameResolverType.FIRST_IP_ALWAYS -> {
        DefaultAddressResolverGroup.INSTANCE
    }
    NameResolverType.ROUND_ROBIN -> {
        RoundRobinNameResolver()
    }
    NameResolverType.OVERWRITE_RESOLVER -> OverwriteNameResolver(hostMap)
}

internal class OverwriteNameResolver(
    private val hostMap: HostsMap?,
) : AddressResolverGroup<InetSocketAddress>() {
    override fun newResolver(executor: EventExecutor): AddressResolver<InetSocketAddress> {
        val nameSpaceMap = object : InetNameResolver(executor) {
            override fun doResolve(inetHost: String, promise: Promise<InetAddress>) {
                val address = hostMap?.resolve(inetHost)?.firstOrNull() ?: inetHost
                try {
                    promise.setSuccess(SocketUtils.addressByName(address))
                } catch (e: UnknownHostException) {
                    promise.setFailure(e)
                }
            }

            override fun doResolveAll(inetHost: String, promise: Promise<MutableList<InetAddress>>) {
                val addresses = hostMap?.resolve(inetHost) ?: listOf(inetHost)
                try {
                    val resolved = addresses.flatMap {
                        SocketUtils.allAddressesByName(inetHost).toList()
                    }.toSet()
                    promise.setSuccess(resolved.toMutableList())
                } catch (e: UnknownHostException) {
                    promise.setFailure(e)
                }
            }
        }
        return RoundRobinInetAddressResolver(executor, nameSpaceMap)
            .asAddressResolver()
    }
}

internal class RoundRobinNameResolver : AddressResolverGroup<InetSocketAddress>() {
    override fun newResolver(executor: EventExecutor): AddressResolver<InetSocketAddress> {
        val defaultNameResolver = DefaultNameResolver(executor)
        return RoundRobinInetAddressResolver(executor, defaultNameResolver)
            .asAddressResolver()
    }
}
