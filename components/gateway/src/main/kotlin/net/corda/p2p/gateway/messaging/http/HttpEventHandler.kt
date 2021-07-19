package net.corda.p2p.gateway.messaging.http

/**
 * Interface for managing Http events such as connection up or down, and received messages.
 */
interface HttpEventHandler {
    fun registerConnectionHandler(handler: (ConnectionChangeEvent) -> Unit)
    fun registerMessageHandler(handler: (HttpMessage) -> Unit)
    fun unregisterConnectionHandlers()
    fun unregisterMessageHandlers()
}