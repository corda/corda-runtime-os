package net.corda.rest.server.impl.websocket

import io.javalin.websocket.WsContext
import org.eclipse.jetty.websocket.api.CloseStatus

/**
 * Close a WebSocket connection.
 */
interface WebSocketCloserService {
    fun close(webSocketContext: WsContext, closeStatus: CloseStatus)
}