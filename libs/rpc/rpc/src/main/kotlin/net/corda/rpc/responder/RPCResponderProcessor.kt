package net.corda.rpc.responder

import java.util.concurrent.CompletableFuture

interface RPCResponderProcessor<TREQ, TRESP> {

    /**
     * The implementation of this functional class will be used to notify you of any requests that need processing
     * @param request
     * @param responseFuture
     */
    fun onUpdate(request: TREQ, responseFuture:CompletableFuture<TRESP>): Unit
}