package net.corda.p2p.gateway.messaging.http

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpObject
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.LastHttpContent
import io.netty.handler.ssl.SslHandshakeCompletionEvent
import io.netty.handler.timeout.IdleStateEvent
import net.corda.p2p.gateway.messaging.http.HttpHelper.Companion.validate
import org.slf4j.Logger
import java.lang.IndexOutOfBoundsException
import java.nio.channels.ClosedChannelException
import javax.net.ssl.SSLException

class HttpChannelHandler(
    private val eventListener: HttpEventListener,
    private val logger: Logger
) : SimpleChannelInboundHandler<HttpObject>() {

    private var messageBodyBuf: ByteBuf? = null
    private var responseCode: HttpResponseStatus? = null

    /**
     * Reads the HTTP objects into a [ByteBuf] and publishes them to all subscribers
     */
    @Suppress("TooGenericExceptionCaught", "ComplexMethod")
    override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpObject) {
        if (msg is HttpResponse) {
            logger.debug("Received response message $msg")
            messageBodyBuf = ctx.alloc().buffer(msg.headers()[HttpHeaderNames.CONTENT_LENGTH].toInt())
            responseCode = msg.status()
        }

        if (msg is HttpRequest) {
            responseCode = msg.validate()
            logger.debug("Received HTTP request from ${ctx.channel().remoteAddress()}\n" +
                    "Protocol version: ${msg.protocolVersion()}\n" +
                    "Hostname: ${msg.headers()[HttpHeaderNames.HOST]?:"unknown"}\n" +
                    "Request URI: ${msg.uri()}\n" +
                    "Content length: ${msg.headers()[HttpHeaderNames.CONTENT_LENGTH]}\n")
            // initialise byte array to read the request into
            if (responseCode!! != HttpResponseStatus.LENGTH_REQUIRED) {
                messageBodyBuf = ctx.alloc().buffer(msg.headers()[HttpHeaderNames.CONTENT_LENGTH].toInt())
            }

            if (HttpUtil.is100ContinueExpected(msg)) {
                send100Continue(ctx)
            }
        }

        if (msg is HttpContent) {
            val content = msg.content()
            if (content.isReadable) {
                logger.debug("Reading message content into local buffer of size ${content.readableBytes()}")
                try {
                    content.readBytes(messageBodyBuf, content.readableBytes())
                } catch (e: IndexOutOfBoundsException) {
                    logger.error("Cannot read request body into buffer. Space not allocated")
                }
            }
        }

        // This message type indicates the entire Http object has been received and the body content can be forwarded to
        // the event processor. No trailing headers should exist
        if (msg is LastHttpContent) {
            logger.debug("Read end of response body $msg")
            val sourceAddress = ctx.channel().remoteAddress()
            val targetAddress = ctx.channel().localAddress()
            val returnByteArray = ByteArray(messageBodyBuf!!.readableBytes())
            messageBodyBuf!!.readBytes(returnByteArray)
            eventListener.onMessage(HttpMessage(responseCode!!, returnByteArray, sourceAddress, targetAddress))

            messageBodyBuf?.release()
            responseCode = null
        }
    }

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

    private fun send100Continue(ctx: ChannelHandlerContext) {
        val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE, Unpooled.EMPTY_BUFFER)
        ctx.write(response)
    }
}