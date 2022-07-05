package net.corda.httprpc.server.impl.apigen.processing.ws

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

internal class WebsocketRouteAdaptor(
    private val routeInfo: RouteInfo,
    private val securityManager: HttpRpcSecurityManager,
    private val credentialResolver: DefaultCredentialResolver
) : WsMessageHandler, WsCloseHandler,
    WsConnectHandler, WsErrorHandler {

    private companion object {
        val log = contextLogger()
    }

    private var channel: DuplexChannel? = null

    override fun handleConnect(ctx: WsConnectContext) {

        log.info("Connected to remote: ${ctx.session.remoteAddress}")

        ServerDuplexChannel(ctx).let { newChannel ->
            channel = newChannel

            val clientWsRequestContext = ClientWsRequestContext(ctx)

            val authorizingSubject = authenticate(clientWsRequestContext, securityManager, credentialResolver)
            authorize(authorizingSubject, clientWsRequestContext.getResourceAccessString())

            val paramsFromRequest = routeInfo.retrieveParameters(clientWsRequestContext)
            val fullListOfParams = listOf(newChannel) + paramsFromRequest

            @Suppress("SpreadOperator")
            routeInfo.invokeDelegatedMethod(*fullListOfParams.toTypedArray())
            newChannel.onConnect?.let { it() }
        }
    }

    override fun handleMessage(ctx: WsMessageContext) {
        requireNotNull(channel).onTextMessage?.let { it(ctx.message()) }
    }

    override fun handleError(ctx: WsErrorContext) {
        requireNotNull(channel).onError?.let { it(ctx.error()) }
    }

    override fun handleClose(ctx: WsCloseContext) {
        requireNotNull(channel).onClose?.let { it(ctx.status(), ctx.reason()) }
    }
}