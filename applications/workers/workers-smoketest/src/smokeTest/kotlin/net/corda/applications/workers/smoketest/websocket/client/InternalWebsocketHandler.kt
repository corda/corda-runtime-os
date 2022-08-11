package net.corda.applications.workers.smoketest.websocket.client

import java.util.Queue

interface InternalWebsocketHandler {
    val messageQueue: Queue<String>
    fun isConnected(): Boolean
    fun send(message: String)
}