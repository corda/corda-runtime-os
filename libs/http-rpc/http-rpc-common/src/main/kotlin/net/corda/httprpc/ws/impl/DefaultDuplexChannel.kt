package net.corda.httprpc.ws.impl

import net.corda.httprpc.ws.DuplexChannel
import net.corda.httprpc.ws.DuplexChannelCloseContext
import net.corda.httprpc.ws.DuplexConnectContext
import net.corda.httprpc.ws.DuplexErrorContext
import net.corda.httprpc.ws.DuplexTextMessageContext
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

/**
 * Default implementation of [DuplexChannel] which does bare minimum. It is supposed to be extended as necessary.
 */
open class DefaultDuplexChannel : DuplexChannel {

    private companion object {
        val log = contextLogger()
    }

    @Volatile
    protected var connectContext: DuplexConnectContext? = null

    override fun onConnect(connectContext: DuplexConnectContext) {
        this.connectContext = connectContext
    }

    override fun onMessage(context: DuplexTextMessageContext) {
        log.info("onMessage() : ${context.message}")
    }

    override fun send(message: String): Future<Void> {
        log.debug { "Sending: $message" }

        return connectContext?.send(message)
            ?: CompletableFuture.failedFuture(IllegalStateException("Channel is not connected"))
    }

    override fun onError(errorContext: DuplexErrorContext) {
        log.info("onError() : $errorContext")
    }

    override fun onClose(context: DuplexChannelCloseContext) {
        connectContext = null
    }

    override fun close() {
        connectContext = null
    }
}