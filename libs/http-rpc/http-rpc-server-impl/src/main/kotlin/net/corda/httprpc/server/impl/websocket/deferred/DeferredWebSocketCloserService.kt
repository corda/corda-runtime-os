package net.corda.httprpc.server.impl.websocket.deferred

import io.javalin.websocket.WsContext
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import net.corda.httprpc.server.impl.websocket.WebSocketCloserService
import net.corda.v5.base.util.contextLogger
import org.apache.commons.lang3.concurrent.BasicThreadFactory
import org.eclipse.jetty.websocket.api.CloseStatus

/**
 * Using a deferred thread pool to close websocket connections on a separate thread.
 */
class DeferredWebSocketCloserService : WebSocketCloserService {

    private companion object {
        val log = contextLogger()
    }

    private val deferredWebsocketClosePool = Executors.newScheduledThreadPool(1,
        BasicThreadFactory.Builder().namingPattern("wsFlowStatusClose-%d").daemon(true).build())

    override fun close(webSocketContext: WsContext, closeStatus: CloseStatus) {
        deferredWebsocketClosePool.schedule({
            if (webSocketContext.session.isOpen) {
                log.info("Closing open session ${webSocketContext.sessionId}: status ${closeStatus.code}, reason: ${closeStatus.phrase}")
            } else {
                log.info("Closing session ${webSocketContext.sessionId} that's already reported closed: " +
                        "status ${closeStatus.code}, reason: ${closeStatus.phrase}")
            }
            webSocketContext.closeSession(closeStatus)
        }, 1, TimeUnit.SECONDS)
    }
}