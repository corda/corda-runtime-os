package net.corda.httprpc.ws

import java.util.concurrent.Future

/**
 * Channel to facilitate full duplex (i.e. two-way communication) like WebSockets protocol.
 * This is a special type which is meant to be used as a first parameter in `GET` endpoint which will be exposed as
 * a Websocket endpoint.
 */
interface DuplexChannel : AutoCloseable {

    /**
     * Allows to asynchronously send a message to the remote side
     */
    fun send(message: String): Future<Void>

    /**
     * Allows to close this communication channel
     */
    override fun close()

    /**
     * Callback to be invoked when connected to remote side
     */
    var onConnect: (() -> Unit)?

    /**
     * Callback to be invoked when text message received from remote side
     */
    var onTextMessage: ((message: String) -> Unit)?

    /**
     * Callback to be invoked in case of an error
     */
    var onError: ((Throwable?) -> Unit)?

    /**
     * Installs a callback to be invoked when connection with remote side is closed.
     * Regardless whether close operation was initiated by our side or remote side.
     */
    var onClose: ((statusCode: Int, reason: String?) -> Unit)?
}