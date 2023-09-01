package net.corda.messaging.api.processor

/**
 * This interface defines a processor of events from a rpc subscription on a feed with requests of type [REQUEST] and
 * responses of type [RESPONSE]
 *
 * If you want to receive events from a from [RPCSubscription] you should implement this interface.
 */
interface SyncRPCProcessor<REQUEST, RESPONSE> {

    /**
     * The implementation of this class will be used to process incoming REQUEST
     * @param request
     *
     * @return the result of the processing of the type RESPONSE
     */
    fun process(request: REQUEST) : RESPONSE

    val requestClass: Class<REQUEST>
    val responseClass: Class<RESPONSE>
}
