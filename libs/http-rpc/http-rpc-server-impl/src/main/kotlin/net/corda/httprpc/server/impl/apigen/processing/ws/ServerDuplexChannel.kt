package net.corda.httprpc.server.impl.apigen.processing.ws

import io.javalin.websocket.WsConnectContext
import java.lang.Exception
import java.lang.IllegalArgumentException
import net.corda.httprpc.ws.DuplexChannel
import org.eclipse.jetty.websocket.api.CloseStatus
import org.eclipse.jetty.websocket.api.StatusCode
import java.util.concurrent.Future

internal class ServerDuplexChannel(private val ctx: WsConnectContext) : DuplexChannel {

    private var errorHook: ((Throwable?) -> Unit)? = null
    private var textMessageHook: ((message: Any) -> Unit)? = null
    private var connectHook: (() -> Unit)? = null
    private var closeHook: ((statusCode: Int, reason: String?) -> Unit)? = null
    private var incomingMessageTypeHook: Class<*>? = null
    private var outgoingMessageTypeHook: Class<*>? = null

    override fun send(message: String): Future<Void> {
        return ctx.send(message)
    }

    override fun send(message: Any): Future<Void> {
        return ctx.send(message)
    }

    override fun close() {
        close(CloseStatus(StatusCode.NORMAL, "Server closed"))
    }

    override fun close(reason: String) {
        close(CloseStatus(StatusCode.NORMAL, reason))
    }

    fun close(closeStatus: CloseStatus) {
        closeHook?.let { it(closeStatus.code, closeStatus.phrase) }
        ctx.closeSession(closeStatus)
    }

    override var onConnect: (() -> Unit)?
        get() = connectHook
        set(value) {
            connectHook = value
        }

    override var onTextMessage: ((message: Any) -> Unit)?
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

    override var incomingMessageType: Class<*>?
        get() = incomingMessageTypeHook
        set(value) {
            incomingMessageTypeHook = value
        }

    override var outgoingMessageType: Class<*>?
        get() = outgoingMessageTypeHook
        set(value) {
            outgoingMessageTypeHook = value
        }

    override fun error(e: Exception) {
        when (e) {
            is IllegalArgumentException -> close(CloseStatus(StatusCode.BAD_DATA, e.message))
            // todo conal - add some more here?
            else -> close(CloseStatus(StatusCode.UNDEFINED, e.message))
        }
    }
}