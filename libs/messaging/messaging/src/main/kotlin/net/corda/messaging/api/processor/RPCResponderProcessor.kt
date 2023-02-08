package net.corda.messaging.api.processor

import java.util.concurrent.CompletableFuture


/**
 * This interface defines a processor of events from a rest subscription on a feed with with requests of type [REQUEST] and
 * responses of type [RESPONSE]
 *
 * If you want to receive events from a from [RPCSubscription] you should implement this interface.
 *
 * Subscribers will receive events as they come in as well as a response future
 *
 * There can be multiple workers servicing RPC request messages
 */
interface RPCResponderProcessor<REQUEST, RESPONSE> {

    /**
     * The implementation of this functional class will be used to notify you of any requests that need processing
     * @param request
     * @param respFuture
     *
     * The implementor must take note to set the future. The underlying subscription will take core of sending the
     * response, but will only do so when the future is completed
     *
     * [onNext] is meant to be asynchronous. As such, it's safe to make external services - provided that when the
     * result of that call is ready, the future is set
     */
    fun onNext(request: REQUEST, respFuture: CompletableFuture<RESPONSE>)
}