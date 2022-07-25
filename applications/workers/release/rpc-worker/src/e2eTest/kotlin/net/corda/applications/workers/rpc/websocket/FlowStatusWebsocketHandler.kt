package net.corda.applications.workers.rpc.websocket

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.util.Queue
import net.corda.flow.rpcops.v1.types.response.FlowStatusResponse
import net.corda.v5.base.util.contextLogger
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.client.NoOpEndpoint

class FlowStatusWebsocketHandler(
    private val messages: Queue<FlowStatusResponse>
): NoOpEndpoint() {

    private companion object {
        val log = contextLogger()
    }

    override fun onWebSocketConnect(session: Session) {
        log.info("onWebSocketConnect : $session")
    }

    override fun onWebSocketClose(statusCode: Int, reason: String?) {
        log.info("Reacting to server closed: $statusCode - $reason")
        super.onWebSocketClose(statusCode, reason)
    }

    override fun onWebSocketText(message: String) {
        log.info("Received message: $message")
        val responseObject = jacksonObjectMapper().readValue(message, FlowStatusResponse::class.java)
        messages.add(responseObject)
    }
}
