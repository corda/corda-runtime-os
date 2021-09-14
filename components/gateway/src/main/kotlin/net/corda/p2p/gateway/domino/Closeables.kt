package net.corda.p2p.gateway.domino

import io.netty.channel.Channel
import io.netty.channel.EventLoopGroup

internal class CloseableNioEventLoopGroup(
    private val group: EventLoopGroup
) : AutoCloseable {
    override fun close() {
        group.shutdownGracefully()
        group.terminationFuture().sync()
    }
}

internal class CloseableChannel(
    private val channel: Channel
) : AutoCloseable {
    override fun close() {
        channel.close().sync()
    }
}
internal class CloseableMap(private val map: MutableMap<*, *>) : AutoCloseable {
    override fun close() {
        map.clear()
    }
}
