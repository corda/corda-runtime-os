package net.corda.p2p.gateway.messaging.http

import io.micrometer.core.instrument.Counter
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.HttpObject
import io.netty.handler.ssl.SslHandshakeCompletionEvent
import io.netty.handler.ssl.SslHandshakeTimeoutException
import io.netty.handler.timeout.IdleStateEvent
import net.corda.metrics.CordaMetrics
import net.corda.metrics.CordaMetrics.NOT_APPLICABLE_TAG_VALUE
import org.slf4j.Logger
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.channels.ClosedChannelException
import javax.net.ssl.SSLException

abstract class BaseHttpChannelHandler(
    private val eventListener: HttpConnectionListener,
    private val logger: Logger,
    private val handlerType: HandlerType,
) : SimpleChannelInboundHandler<HttpObject>() {

    private companion object {
        val messagesNotToLog = listOf(
            "unrecognized_name",
            "Unrecognized server name indication",
        )
    }

    private var messageBodyBuf: ByteBuf? = null

    protected fun allocateBodyBuffer(ctx: ChannelHandlerContext, bytes: Int) {
        messageBodyBuf = ctx.alloc().buffer(0, bytes)
    }

    protected fun readBytesIntoBodyBuffer(buffer: ByteBuf) {
        messageBodyBuf!!.writeBytes(buffer)
    }

    protected fun releaseBodyBuffer() {
        messageBodyBuf?.release()
        messageBodyBuf = null
    }

    protected fun readBytesFromBodyBuffer(): ByteArray {
        val byteArray = ByteArray(messageBodyBuf!!.readableBytes())
        messageBodyBuf!!.readBytes(byteArray)
        return byteArray
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        val ch = ctx.channel()
        logger.info("New client connection ${ch.id()} from ${ch.localAddress()} to ${ch.remoteAddress()}")
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        val ch = ctx.channel()
        logger.info("Closed client connection ${ch.id()} from ${ch.localAddress()} to ${ch.remoteAddress()}")
        messageBodyBuf?.let {
            if (it.refCnt() > 0) {
                releaseBodyBuffer()
            }
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
                    getConnectionMetric(ctx.channel().remoteAddress(), "success").increment()
                    eventListener.onOpen(HttpConnectionEvent(ch))
                } else {
                    getConnectionMetric(ctx.channel().remoteAddress(), "failure").increment()
                    val cause = evt.cause()
                    when {
                        cause is ClosedChannelException -> logger.warn("SSL handshake closed early")
                        cause is SslHandshakeTimeoutException -> logger.warn("SSL handshake timed out")
                        cause is SSLException && (cause.message?.contains("close_notify") == true) -> {
                            logger.warn("Received close_notify during handshake")
                        }
                        cause is SSLException && (cause.message?.contains("internal_error") == true) -> {
                            logger.warn("Received internal_error during handshake")
                        }
                        cause is SSLException && (cause.message?.contains("unrecognized_name") == true) -> {
                            logger.warn(
                                "Unrecognized server name error." +
                                    "This is most likely due to mismatch between the certificates" +
                                    " subject alternative name and the host name.",
                            )
                        }
                        cause is SSLException && (cause.message?.contains("Unrecognized server name indication") == true) -> {
                            logger.warn(
                                "Unrecognized server name error." +
                                    "This is most likely due to mismatch between the" +
                                    " certificates subject alternative name and the host name.",
                            )
                        }
                        else -> logger.warn("Handshake failure ${evt.cause().message}", evt.cause())
                    }
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
        val message = cause.message ?: ""
        if (!messagesNotToLog.any {
                message.contains(it)
            }
        ) {
            logger.warn("Closing channel due to unrecoverable exception ${cause.message}", cause)
        }
        ctx.close()
    }

    private fun getConnectionMetric(remoteAddress: SocketAddress, connectionResult: String): Counter {
        // The address is always expected to be an InetSocketAddress, but populating the tag in any other case because prometheus
        // requires the same set of tags to be populated for a specific metric name.
        val remoteAddressValue = if (remoteAddress is InetSocketAddress) {
            remoteAddress.hostString
        } else {
            NOT_APPLICABLE_TAG_VALUE
        }

        return when (handlerType) {
            HandlerType.SERVER -> getServerCounter(remoteAddressValue, connectionResult)
            HandlerType.CLIENT -> getClientCounter(remoteAddressValue, connectionResult)
        }
    }

    private fun getServerCounter(remoteAddress: String, connectionResult: String): Counter {
        return CordaMetrics.Metric.InboundGatewayConnections.builder()
            .withTag(CordaMetrics.Tag.ConnectionResult, connectionResult)
            .withTag(CordaMetrics.Tag.SourceEndpoint, remoteAddress)
            .build()
    }

    private fun getClientCounter(remoteAddress: String, connectionResult: String): Counter {
        return CordaMetrics.Metric.OutboundGatewayConnections.builder()
            .withTag(CordaMetrics.Tag.ConnectionResult, connectionResult)
            .withTag(CordaMetrics.Tag.DestinationEndpoint, remoteAddress)
            .build()
    }
}

enum class HandlerType {
    CLIENT,
    SERVER,
}
