package net.corda.messaging.api.processor

import java.util.concurrent.CompletableFuture


/**
 * This interface defines a processor of events from a rpc subscription on a feed with with requests of type [TREQ] and
 * responses of type [TRESP]
 *
 * If you want to receive events from a from [RPCSubscription] you should implement this interface.
 *
 * Subscribers will receive events as they come in as well as a response future
 */
interface RPCResponderProcessor<TREQ, TRESP> {

    /**
     * The implementation of this functional class will be used to notify you of any requests that need processing
     * @param request
     * @param respFuture
     */
    fun onNext(request: TREQ, respFuture: CompletableFuture<TRESP>)

    /**
     * [requestType] and [responseType] to easily get the request and response types the processor operates on.
     */
    val requestType: Class<TREQ>
    val responseType: Class<TRESP>
}