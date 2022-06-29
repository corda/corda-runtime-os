package net.corda.httprpc.server.impl.apigen.processing.ws

import io.javalin.websocket.WsCloseContext
import io.javalin.websocket.WsConnectContext
import io.javalin.websocket.WsErrorContext
import io.javalin.websocket.WsMessageContext
import net.corda.httprpc.ws.DuplexChannelCloseContext
import net.corda.httprpc.ws.DuplexConnectContext
import net.corda.httprpc.ws.DuplexErrorContext
import net.corda.httprpc.ws.DuplexTextMessageContext
import java.util.concurrent.Future

internal class DuplexTextMessageContextImpl(override val message: String) : DuplexTextMessageContext {
    companion object {
        fun from(messageCtx: WsMessageContext): DuplexTextMessageContext {
            return DuplexTextMessageContextImpl(messageCtx.message())
        }
    }
}

@Suppress("unused")
internal class DuplexCloseContextImpl(private val wsCloseContext: WsCloseContext) : DuplexChannelCloseContext {
    companion object {
        fun from(wsCloseContext: WsCloseContext): DuplexChannelCloseContext {
            return DuplexCloseContextImpl(wsCloseContext)
        }
    }
}

internal class DuplexConnectContextImpl(private val wsConnectContext: WsConnectContext) : DuplexConnectContext {
    companion object {
        fun from(wsConnectContext: WsConnectContext): DuplexConnectContext {
            return DuplexConnectContextImpl(wsConnectContext)
        }
    }

    override fun send(message: String): Future<Void> {
        return wsConnectContext.send(message)
    }
}

@Suppress("unused")
internal class DuplexErrorContextImpl(private val wsErrorContext: WsErrorContext) : DuplexErrorContext {
    companion object {
        @Suppress("unused")
        fun from(wsErrorContext: WsErrorContext): DuplexErrorContext {
            return DuplexErrorContextImpl(wsErrorContext)
        }
    }
}