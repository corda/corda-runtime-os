package net.corda.applications.workers.smoketest.websocket.client

interface InternalWebsocketHandler {
    val messageQueueSnapshot: List<String>
    fun isConnected(): Boolean
    fun send(message: String)
}