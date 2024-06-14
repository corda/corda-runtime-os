package net.corda.rest.server.impl.websocket

import io.javalin.websocket.WsConnectContext
import net.corda.rest.ws.DuplexChannel
import org.eclipse.jetty.websocket.api.CloseStatus
import org.eclipse.jetty.websocket.api.StatusCode
import java.lang.Exception
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Future

internal class ServerDuplexChannel(
    private val ctx: WsConnectContext,
    private val webSocketCloserService: WebSocketCloserService,
    override val id: String
) : DuplexChannel {

    private var errorHook: ((Throwable?) -> Unit)? = null
    private var textMessageHook: ((message: String) -> Unit)? = null
    private var connectHook: (() -> Unit)? = null
    private var closeHook: ((statusCode: Int, reason: String?) -> Unit)? = null

    private val executorService = Executors.newCachedThreadPool()

    override fun send(message: String): Future<Void> {
        return CompletableFuture.runAsync({ ctx.send(message) }, executorService)
    }

    override fun send(message: Any): Future<Void> {
        return CompletableFuture.runAsync({ ctx.send(message) }, executorService)
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
        webSocketCloserService.close(ctx, closeStatus)
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
