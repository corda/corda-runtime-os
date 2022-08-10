package net.corda.httprpc.server.impl.websocket

import io.javalin.websocket.WsConnectContext
import java.lang.Exception
import net.corda.httprpc.ws.DuplexChannel
import org.eclipse.jetty.websocket.api.CloseStatus
import org.eclipse.jetty.websocket.api.StatusCode
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import net.corda.v5.base.util.contextLogger

internal class ServerDuplexChannel(
    private val ctx: WsConnectContext,
    private val deferredWebsocketClosePool: ScheduledExecutorService
) : DuplexChannel {

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
        close(e.mapToWsStatusCode())
    }

    fun close(closeStatus: CloseStatus) {
        closeHook?.let { it(closeStatus.code, closeStatus.phrase) }
        // Since this call can be made from `onConnect` it is best deferring calling close later on from a separate
        // thread or else Javalin may end-up in the unusable state sometimes.
        deferredWebsocketClosePool.schedule({
            if (ctx.session.isOpen) {
                logger.info("Closing open session with status ${closeStatus.code}, reason: ${closeStatus.phrase}")
            } else {
                logger.info("Closing session that reported not open with Status ${closeStatus.code}, reason: ${closeStatus.phrase}")
            }
            ctx.closeSession(closeStatus)
        }, 1, TimeUnit.SECONDS)
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