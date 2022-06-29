package net.corda.httprpc.ws

import java.util.concurrent.Future

/**
 * Channel to facilitate full duplex (i.e. two-way communication) like WebSockets protocol
 */
interface DuplexChannel {
    /**
     * Allows reacting on the event when remote side is connected
     */
    fun onConnect(connectContext: DuplexConnectContext)

    /**
     * Allows reacting on the event when remote side sends us a message
     */
    fun onMessage(context: DuplexTextMessageContext)

    /**
     * Allows to asynchronously send a message to the remote side
     */
    fun send(message: String): Future<Void>

    /**
     * Allows reacting when remote side reports an error
     */
    fun onError(errorContext: DuplexErrorContext)

    /**
     * Allows reacting when remote closes this communication channel
     */
    fun onClose(context: DuplexChannelCloseContext)

    /**
     * Allows to close this communication channel
     */
    fun close()
}

interface DuplexTextMessageContext {
    val message: String
}

interface DuplexChannelCloseContext

interface DuplexConnectContext {
    fun send(message: String): Future<Void>
}

interface DuplexErrorContext