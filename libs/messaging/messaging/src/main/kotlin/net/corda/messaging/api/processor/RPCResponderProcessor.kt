package net.corda.messaging.api.processor

import java.util.concurrent.CompletableFuture

interface RPCResponderProcessor<TREQ, TRESP> {

    /**
     * The implementation of this functional class will be used to notify you of any requests that need processing
     * @param request
     */
    fun onNext(request: TREQ) : CompletableFuture<TRESP>

    val requestType: Class<TREQ>
    val responseType: Class<TRESP>
}