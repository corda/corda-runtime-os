package net.corda.httprpc.server.impl.websocket

import io.javalin.http.UnauthorizedResponse
import io.javalin.websocket.WsCloseContext
import io.javalin.websocket.WsCloseHandler
import io.javalin.websocket.WsConnectContext
import io.javalin.websocket.WsConnectHandler
import io.javalin.websocket.WsErrorContext
import io.javalin.websocket.WsErrorHandler
import io.javalin.websocket.WsMessageContext
import io.javalin.websocket.WsMessageHandler
import java.util.concurrent.ConcurrentHashMap
import net.corda.httprpc.server.impl.apigen.processing.RouteInfo
import net.corda.httprpc.server.impl.context.ClientWsRequestContext
import net.corda.httprpc.server.impl.context.ContextUtils.authenticate
import net.corda.httprpc.server.impl.context.ContextUtils.authorize
import net.corda.httprpc.server.impl.context.ContextUtils.retrieveParameters
import net.corda.httprpc.server.impl.security.RestAuthenticationProvider
import net.corda.httprpc.server.impl.security.provider.credentials.DefaultCredentialResolver
import net.corda.httprpc.ws.DuplexChannel
import org.slf4j.LoggerFactory
import org.eclipse.jetty.websocket.api.CloseStatus
import org.eclipse.jetty.websocket.api.StatusCode.POLICY_VIOLATION

typealias SessionId = String

/**
 * This adapter class handles connect, message, close and error events for all websocket connections for an endpoint i.e. if clientA
 * and clientB are both connected to the server, clientA sends a message, clientB sends a message, both messages will be handled by this
 * handleMessage function.
 *
 * WsContext contains a SessionId which is unique per connection and used as an identifier for a duplex channel and key in
 * [channelsBySessionId] map.
 */
internal class WebSocketRouteAdaptor(
    private val routeInfo: RouteInfo,
    private val restAuthProvider: RestAuthenticationProvider,
    private val credentialResolver: DefaultCredentialResolver,
    private val webSocketCloserService: WebSocketCloserService,
    private val webSocketIdleTimeoutMs: Long
) : WsMessageHandler, WsCloseHandler, WsConnectHandler, WsErrorHandler, AutoCloseable {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val channelsBySessionId = ConcurrentHashMap<SessionId, DuplexChannel>()

    // The handler is called when a WebSocket client connects.
    @Suppress("NestedBlockDepth")
    override fun handleConnect(ctx: WsConnectContext) {
        try {
            channelsBySessionId[ctx.sessionId]?.let {
                log.info("Session with id ${ctx.sessionId} already exists, overwriting and closing the old session.")
                it.close("New session overwriting old session with id ${ctx.sessionId}")
            }
            log.info("Connected to remote: ${ctx.session.remoteAddress}")

            ServerDuplexChannel(ctx, webSocketCloserService, ctx.sessionId).let { newChannel ->
                channelsBySessionId[ctx.sessionId] = newChannel

                ctx.session.idleTimeout = webSocketIdleTimeoutMs
                val clientWsRequestContext = ClientWsRequestContext(ctx)

                try {
                    val authorizingSubject = authenticate(clientWsRequestContext, restAuthProvider, credentialResolver)
                    authorize(authorizingSubject, clientWsRequestContext.getResourceAccessString())

                    val paramsFromRequest = routeInfo.retrieveParameters(clientWsRequestContext)
                    val fullListOfParams = listOf(newChannel) + paramsFromRequest

                    @Suppress("SpreadOperator")
                    routeInfo.invokeDelegatedMethod(*fullListOfParams.toTypedArray())
                    newChannel.onConnect?.invoke()
                } catch (ex: UnauthorizedResponse) {
                    "Websocket operation not permitted".let {
                        log.warn("$it - ${ex.message}")
                        newChannel.close(CloseStatus(POLICY_VIOLATION, it))
                    }
                }
            }
        } catch (th: Throwable) {
            log.error("Unexpected exception in handleConnect", th)
        }
    }

    // The handler is called when a WebSocket client sends a String message.
    override fun handleMessage(ctx: WsMessageContext) {
        try {
            // incoming messages could be malicious. We won't do anything with the message unless an onTextMessage
            // hook has been defined. The hook will be responsible for ensuring the messages respect the protocol
            // and terminate connections when malicious messages arrive.
            requireNotNull(channelsBySessionId[ctx.sessionId]).onTextMessage?.invoke(ctx.message())
                ?: log.info("Inbound messages are not supported.")
        } catch (th: Throwable) {
            log.error("Exception during message handling", th)
        }
    }

    // The handler is called when an error is detected.
    override fun handleError(ctx: WsErrorContext) {
        try {
            requireNotNull(channelsBySessionId[ctx.sessionId]).onError?.invoke(ctx.error())
        } catch (th: Throwable) {
            log.error("Unexpected exception in handleError", th)
        }
    }

    // The handler is called when a WebSocket client closes the connection.
    override fun handleClose(ctx: WsCloseContext) {
        try {
            channelsBySessionId.remove(ctx.sessionId)?.onClose?.invoke(ctx.status(), ctx.reason())
        } catch (th: Throwable) {
            log.error("Unexpected exception in handleClose", th)
        }
    }

    /**
     * Close the RouteAdaptor, and close all duplex channel connections.
     */
    override fun close() {
        channelsBySessionId.forEach {
            it.value.close("All duplex connections closed.")
        }
    }
}