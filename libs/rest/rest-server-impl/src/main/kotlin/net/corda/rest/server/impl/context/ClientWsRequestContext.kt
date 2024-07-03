package net.corda.rest.server.impl.context

import io.javalin.security.BasicAuthCredentials
import io.javalin.websocket.WsContext
import java.lang.UnsupportedOperationException

/**
 * Implementation of [ClientRequestContext] which implements functionality using [WsContext].
 */
class ClientWsRequestContext(private val ctx: WsContext) : ClientRequestContext {

    override val method = "WS"

    override fun header(header: String): String? = ctx.header(header)

    override val pathParamMap: Map<String, String>
        get() = ctx.pathParamMap()

    override val queryParams: Map<String, List<String>>
        get() = ctx.queryParamMap()

    override val matchedPath: String
        get() = ctx.matchedPath()

    override val path: String
        // `path()` is not accessible in the context, the best we can do is to use `matchedPath` which will not have
        // path parameters resolved.
        get() = ctx.matchedPath()

    override fun formParamMap(): Map<String, List<String>> {
        throw UnsupportedOperationException()
    }

    override fun basicAuthCredentialsExist(): Boolean {
        TODO("Not supported")
    }

    override fun basicAuthCredentials(): BasicAuthCredentials {
        TODO("Not supported")
    }

    override val queryString: String?
        get() = ctx.queryString()
}
