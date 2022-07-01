package net.corda.httprpc.server.impl.apigen.processing.ws

import io.javalin.websocket.WsConnectContext
import net.corda.httprpc.ws.DuplexChannel
import net.corda.v5.base.util.contextLogger
import org.eclipse.jetty.websocket.api.StatusCode
import java.util.concurrent.Future

internal class ServerDuplexChannel(private val ctx: WsConnectContext) : DuplexChannel {

    private var errorHook: ((Throwable?) -> Unit)? = null
    private var textMessageHook: ((message: String) -> Unit)? = null
    private var connectHook: (() -> Unit)? = null
    private var closeHook: ((statusCode: Int, reason: String?) -> Unit)? = null

    override fun send(message: String): Future<Void> {
        return ctx.send(message)
    }

    override fun close() {
        closeHook?.let { it(StatusCode.NORMAL, "Server closed") }
        ctx.closeSession()
    }

    override var onConnect: (() -> Unit)?
        get() = connectHook
        set(value) {
            connectHook = value
        }

    override var onTextMessage: ((message: String) -> Unit)?
        get() = textMessageHook
        set(value) {
            textMessageHook = value
        }

    override var onError: ((Throwable?) -> Unit)?
        get() = errorHook
        set(value) {
            errorHook = value
        }

    override var onClose: ((statusCode: Int, reason: String?) -> Unit)?
        get() = closeHook
        set(value) {
            closeHook = value
        }
}