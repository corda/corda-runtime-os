package net.corda.v5.application.flows

import net.corda.v5.application.marshalling.MarshallingService

/**
 * A wrapper around the request data for RPC started flows.
 *
 * An RPC started flow will receive an instance of this interface, which can be used to retrieve the request body.
 */
interface RPCRequestData {

    /**
     * Get the request body for this RPC started flow.
     *
     * @return The request body.
     */
    fun getRequestBody() : String

    /**
     * Get the request body and deserialize it into the given type, using a marshalling service.
     *
     * The selected marshalling service will determine what format data is returned.
     *
     * @param marshallingService The marshalling service to use to deserialize this request body.
     * @return JSON representation of the input request body.
     */
    fun <T> getRequestBodyAs(marshallingService: MarshallingService, clazz: Class<T>) : T
}

inline fun <reified T> RPCRequestData.getRequestBodyAs(marshallingService: MarshallingService) : T {
    return getRequestBodyAs(marshallingService, T::class.java)
}