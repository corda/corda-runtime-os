package net.corda.v5.application.flows

import net.corda.v5.application.serialization.JsonMarshallingService

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
     * Get the request body and deserialize it into the given type, using a JSON marshalling service.
     *
     * @param jsonMarshallingService The JSON marshalling service to use to deserialize this request body.
     * @return JSON representation of the input request body.
     */
    fun <T> getRequestBodyAs(jsonMarshallingService: JsonMarshallingService, clazz: Class<T>) : T
}

inline fun <reified T> RPCRequestData.getRequestBodyAs(jsonMarshallingService: JsonMarshallingService) : T {
    return getRequestBodyAs(jsonMarshallingService, T::class.java)
}