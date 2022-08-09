package net.corda.httprpc.server.impl.apigen.processing.ws

import io.javalin.websocket.WsConnectContext
import java.lang.Exception
import java.lang.IllegalArgumentException
import net.corda.httprpc.ws.DuplexChannel
import org.eclipse.jetty.websocket.api.CloseStatus
import org.eclipse.jetty.websocket.api.StatusCode
import java.util.concurrent.Future
import net.corda.httprpc.ws.WebSocketProtocolViolationException
import net.corda.v5.base.util.contextLogger

internal class ServerDuplexChannel(private val ctx: WsConnectContext) : DuplexChannel {

    private companion object {
        val logger = contextLogger()
    }

    private var errorHook: ((Throwable?) -> Unit)? = null
    private var textMessageHook: ((message: String) -> Unit)? = null
    private var connectHook: (() -> Unit)? = null
    private var closeHook: ((statusCode: Int, reason: String?) -> Unit)? = null

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

    override fun error(e: Exception) {
        when (e) {
            is IllegalArgumentException -> close(CloseStatus(StatusCode.BAD_DATA, e.message))
            is WebSocketProtocolViolationException -> close(CloseStatus(StatusCode.POLICY_VIOLATION, e.message))
            else -> close(CloseStatus(StatusCode.NORMAL, e.message))
        }
    }

    fun close(closeStatus: CloseStatus) {
        closeHook?.let { it(closeStatus.code, closeStatus.phrase) }
        if(ctx.session.isOpen) {
            logger.info("ServerDuplexChannel closing open session with status ${closeStatus.code}, reason: ${closeStatus.phrase}.")
            ctx.closeSession(closeStatus)
        } else {
            logger.warn("ServerDuplexChannel attempted to close session (${ctx.session.remoteAddress}) but it was not open.")
        }
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