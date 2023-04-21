package net.corda.p2p.gateway.messaging.http

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpObject
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.LastHttpContent
import net.corda.p2p.gateway.messaging.http.HttpHelper.Companion.createResponse
import net.corda.p2p.gateway.messaging.http.HttpHelper.Companion.validate
import net.corda.utilities.debug
import org.slf4j.Logger
import java.lang.IndexOutOfBoundsException

class HttpServerChannelHandler(private val serverListener: HttpServerListener,
                               private val maxRequestSize: Long,
                               private val urlPath: String,
                               private val logger: Logger): BaseHttpChannelHandler(serverListener, logger) {

    private var responseCode: HttpResponseStatus? = null

    /**
     * Reads the HTTP objects into a [ByteBuf] and publishes them to all subscribers
     */
    @Suppress("ComplexMethod")
    override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpObject) {
        if (msg is HttpRequest) {
            responseCode = msg.validate(maxRequestSize, urlPath)
            if (responseCode != HttpResponseStatus.OK) {
                logger.warn ("Received invalid HTTP request from ${ctx.channel().remoteAddress()}\n" +
                        "Protocol version: ${msg.protocolVersion()}\n" +
                        "Hostname: ${msg.headers()[HttpHeaderNames.HOST]?:"unknown"}\n" +
                        "Request URI: ${msg.uri()}\n and the response code was $responseCode.")

                val response = createResponse(null, responseCode!!)
                // if validation failed, we eagerly close the connection in a blocking fashion so that we do not process anything more.
                ctx.writeAndFlush(response).get()
                ctx.close().get()
                return
            }

            logger.debug { "Received HTTP request from ${ctx.channel().remoteAddress()}\n" +
                    "Protocol version: ${msg.protocolVersion()}\n" +
                    "Hostname: ${msg.headers()[HttpHeaderNames.HOST]?:"unknown"}\n" +
                    "Request URI: ${msg.uri()}\n" +
                    "Content length: ${msg.headers()[HttpHeaderNames.CONTENT_LENGTH]}\n" }

            // initialise byte array to read the request into
            allocateBodyBuffer(ctx, msg.headers()[HttpHeaderNames.CONTENT_LENGTH].toInt())

            if (HttpUtil.is100ContinueExpected(msg)) {
                send100Continue(ctx)
            }
        }

        if (msg is HttpContent) {
            val content = msg.content()
            if (content.isReadable) {
                logger.debug { "Reading message content into local buffer of size ${content.readableBytes()}" }
                try {
                    readBytesIntoBodyBuffer(content)
                } catch (e: IndexOutOfBoundsException) {
                    logger.error("Cannot read request body into buffer. " +
                            "It exceeded space specified in ${HttpHeaderNames.CONTENT_LENGTH} header.")
                    val response = createResponse(null, HttpResponseStatus.BAD_REQUEST)
                    ctx.writeAndFlush(response).get()
                    ctx.close().get()
                    releaseBodyBuffer()
                    return
                }
            }
        }

        // This message type indicates the entire Http object has been received and the body content can be forwarded to
        // the event processor. No trailing headers should exist
        if (msg is LastHttpContent) {
            logger.debug { "Read end of response body $msg" }
            val returnByteArray = readBytesFromBodyBuffer()
            val sourceAddress = ctx.channel().remoteAddress()
            val targetAddress = ctx.channel().localAddress()
            serverListener.onRequest(HttpRequest(returnByteArray, sourceAddress, targetAddress))
            releaseBodyBuffer()
            responseCode = null
        }

    }


    private fun send100Continue(ctx: ChannelHandlerContext) {
        val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE, Unpooled.EMPTY_BUFFER)
        ctx.write(response)
    }
}