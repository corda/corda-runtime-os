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
import net.corda.httprpc.server.impl.apigen.processing.RouteInfo
import net.corda.httprpc.server.impl.context.ClientWsRequestContext
import net.corda.httprpc.server.impl.context.ContextUtils.authenticate
import net.corda.httprpc.server.impl.context.ContextUtils.authorize
import net.corda.httprpc.server.impl.context.ContextUtils.retrieveParameters
import net.corda.httprpc.server.impl.security.HttpRpcSecurityManager
import net.corda.httprpc.server.impl.security.provider.credentials.DefaultCredentialResolver
import net.corda.httprpc.ws.DuplexChannel
import net.corda.v5.base.util.contextLogger
import org.eclipse.jetty.websocket.api.CloseStatus
import org.eclipse.jetty.websocket.api.StatusCode.POLICY_VIOLATION

internal class WebsocketRouteAdaptor(
    private val routeInfo: RouteInfo,
    private val securityManager: HttpRpcSecurityManager,
    private val credentialResolver: DefaultCredentialResolver,
    private val webSocketCloserService: WebSocketCloserService
) : WsMessageHandler, WsCloseHandler,
    WsConnectHandler, WsErrorHandler {

    private companion object {
        val log = contextLogger()
        const val WEBSOCKET_IDLE_TIMEOUT = 20000L
    }

    @Volatile
    private var channel: DuplexChannel? = null

    // The handler is called when a WebSocket client connects.
    @Suppress("NestedBlockDepth")
    override fun handleConnect(ctx: WsConnectContext) {
        try {
            log.info("Connected to remote: ${ctx.session.remoteAddress}")

            ServerDuplexChannel(ctx, webSocketCloserService).let { newChannel ->
                channel = newChannel

                ctx.session.idleTimeout = WEBSOCKET_IDLE_TIMEOUT
                val clientWsRequestContext = ClientWsRequestContext(ctx)

                try {
                    val authorizingSubject = authenticate(clientWsRequestContext, securityManager, credentialResolver)
                    authorize(authorizingSubject, clientWsRequestContext.getResourceAccessString())

                    val paramsFromRequest = routeInfo.retrieveParameters(clientWsRequestContext)
                    val fullListOfParams = listOf(newChannel) + paramsFromRequest

                    @Suppress("SpreadOperator")
                    routeInfo.invokeDelegatedMethod(*fullListOfParams.toTypedArray())
                    newChannel.onConnect?.let { it() }
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
            requireNotNull(channel).onTextMessage?.invoke(ctx.message())
                ?: log.info("Inbound messages are not supported.")
        } catch (th: Throwable) {
            log.error("Unexpected exception in handleMessage", th)
        }
    }

    // The handler is called when an error is detected.
    override fun handleError(ctx: WsErrorContext) {
        try {
            requireNotNull(channel).onError?.let { it(ctx.error()) }
        } catch (th: Throwable) {
            log.error("Unexpected exception in handleError", th)
        }
    }

    // The handler is called when a WebSocket client closes the connection.
    override fun handleClose(ctx: WsCloseContext) {
        try {
            requireNotNull(channel).onClose?.let { it(ctx.status(), ctx.reason()) }
        } catch (th: Throwable) {
            log.error("Unexpected exception in handleClose", th)
        }
    }
}