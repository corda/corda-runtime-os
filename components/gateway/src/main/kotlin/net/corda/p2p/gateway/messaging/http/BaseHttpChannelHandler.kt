package net.corda.p2p.gateway.messaging.http

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.HttpObject
import io.netty.handler.ssl.SslHandshakeCompletionEvent
import io.netty.handler.timeout.IdleStateEvent
import org.slf4j.Logger
import java.nio.channels.ClosedChannelException
import javax.net.ssl.SSLException

abstract class BaseHttpChannelHandler(private val eventListener: HttpConnectionListener,
                                      private val logger: Logger): SimpleChannelInboundHandler<HttpObject>() {

    protected var messageBodyBuf: ByteBuf? = null

    override fun channelActive(ctx: ChannelHandlerContext) {
        val ch = ctx.channel()
        logger.info("New client connection ${ch.id()} from ${ch.localAddress()} to ${ch.remoteAddress()}")
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        val ch = ctx.channel()
        logger.info("Closed client connection ${ch.id()} from ${ch.localAddress()} to ${ch.remoteAddress()}")
        messageBodyBuf?.let {
            if (it.refCnt() > 0)
                it.release()
        }
        eventListener.onClose(HttpConnectionEvent(ch))
        ctx.fireChannelInactive()
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        when (evt) {
            is SslHandshakeCompletionEvent -> {
                if (evt.isSuccess) {
                    val ch = ctx.channel()
                    logger.info("Handshake with ${ctx.channel().remoteAddress()} successful")
                    eventListener.onOpen(HttpConnectionEvent(ch))
                } else {
                    val cause = evt.cause()
                    if (cause is ClosedChannelException) {
                        logger.warn("SSL handshake closed early")
                    } else if (cause is SSLException && cause.message == "handshake timed out") {
                        logger.warn("SSL handshake timed out")
                    }
                    logger.error("Handshake failure ${evt.cause().message}")
                    ctx.close()
                }
            }
            is IdleStateEvent -> {
                val ch = ctx.channel()
                logger.debug("Closing connection with ${ch.remoteAddress()} due to inactivity")
                ctx.close()
            }
        }
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        // Nothing more to read from the transport in this event-loop run. Simply flush
        ctx.flush()
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.warn("Closing channel due to unrecoverable exception ${cause.message}")
        logger.debug(cause.stackTraceToString())
        ctx.close()
    }

}