package net.corda.e2etest.utilities.websocket.client

interface InternalWebsocketHandler {
    val messageQueueSnapshot: List<String>
    fun isConnected(): Boolean
    fun send(message: String)
}