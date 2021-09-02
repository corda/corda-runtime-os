package net.corda.messaging.api.subscription

import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.processor.RPCResponderProcessor

interface RPCResponder<TREQ, TRESP> : Lifecycle {

    /**
     * Register a processor for the [RPCResponder] to handle all inbound requests
     */
    fun registerProcessor(rpcResponderProcessor: RPCResponderProcessor<TREQ, TRESP>): AutoCloseable

    /**
     * Send response via RPC
     */
    fun sendResponse(req: TREQ)

}