package net.corda.httprpc.server.impl.context

import io.javalin.websocket.WsContext

/**
 * Implementation of [ClientRequestContext] which implements functionality using [WsContext].
 */
class ClientWsRequestContext(private val ctx: WsContext) : ClientRequestContext {

    override val pathParamMap: Map<String, String>
        get() = ctx.pathParamMap()

    override val queryParams: Map<String, List<String>>
        get() = ctx.queryParamMap()

    override val matchedPath: String
        get() = ctx.matchedPath()
}