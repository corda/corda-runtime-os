package net.corda.rpc.responder

import net.corda.lifecycle.Lifecycle
import java.util.concurrent.CompletableFuture

interface RPCResponder: Lifecycle {

    /**
     * Register a processor for the [RPCResponder] to handle all inbound requests
     */
    fun registerProcessor(rpcResponderProcessor: RPCResponderProcessor): AutoCloseable

    /**
     * Send response via RPC
     */
    fun sendResponse(): CompletableFuture<Unit>

}