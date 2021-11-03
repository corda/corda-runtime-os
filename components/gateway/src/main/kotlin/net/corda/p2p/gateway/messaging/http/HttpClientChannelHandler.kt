package net.corda.p2p.gateway.messaging.http

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpObject
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.LastHttpContent
import org.slf4j.Logger
import java.lang.IndexOutOfBoundsException

class HttpClientChannelHandler(private val clientListener: HttpClientListener,
                               private val logger: Logger): BaseHttpChannelHandler(clientListener, logger) {

    private var responseCode: HttpResponseStatus? = null

    /**
     * Reads the HTTP objects into a [ByteBuf] and publishes them to all subscribers
     */
    @Suppress("TooGenericExceptionCaught", "ComplexMethod")
    override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpObject) {
        if (msg is HttpResponse) {
            logger.debug("Received response message $msg")
            allocateBodyBuffer(ctx, msg.headers()[HttpHeaderNames.CONTENT_LENGTH].toInt())
            responseCode = msg.status()
        }

        if (msg is HttpContent) {
            val content = msg.content()
            if (content.isReadable) {
                logger.debug("Reading message content into local buffer of size ${content.readableBytes()}")
                try {
                    readBytesIntoBodyBuffer(content)
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
            val returnByteArray = readBytesFromBodyBuffer()
            clientListener.onResponse(HttpResponse(responseCode!!, returnByteArray, sourceAddress, targetAddress))

            releaseBodyBuffer()
            responseCode = null
        }
    }

}