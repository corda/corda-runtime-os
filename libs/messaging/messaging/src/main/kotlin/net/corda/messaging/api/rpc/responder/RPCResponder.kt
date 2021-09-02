package net.corda.messaging.api.rpc.responder

import net.corda.lifecycle.Lifecycle
import java.util.concurrent.CompletableFuture

interface RPCResponder<TREQ, TRESP> : Lifecycle {

    /**
     * Register a processor for the [RPCResponder] to handle all inbound requests
     */
    fun registerProcessor(rpcResponderProcessor: RPCResponderProcessor<TREQ, TRESP>): AutoCloseable

    /**
     * Send response via RPC
     */
    fun sendResponse(req: TREQ): CompletableFuture<Unit>

}