package net.corda.p2p.gateway.domino

import io.netty.channel.Channel
import io.netty.channel.EventLoopGroup

internal fun closeable(resource: Any): AutoCloseable {
    return when (resource) {
        is AutoCloseable -> resource
        is EventLoopGroup -> AutoCloseable {
            resource.shutdownGracefully()
            resource.terminationFuture().sync()
        }
        is Channel -> AutoCloseable {
            resource.close().sync()
        }
        is MutableMap<*, *> -> AutoCloseable {
            resource.clear()
        }
        is Function0<*> -> AutoCloseable {
            resource.invoke()
        }
        else -> throw DominoException("Can not create resource from $resource")
    }
}
