@file:JvmName("RPCRequestUtils")
package net.corda.v5.application.flows

import net.corda.v5.application.marshalling.MarshallingService

/**
 * [RPCRequestData] wraps the `requestData` parameter of the HTTP call that triggered a [RPCStartableFlow].
 *
 * A [RPCStartableFlow] receives an instance of this interface, which can be used to retrieve the request body.
 *
 * @see RPCStartableFlow
 */
interface RPCRequestData {

    /**
     * Gets the request body for the [RPCStartableFlow].
     *
     * @return The request body.
     */
    fun getRequestBody() : String

    /**
     * Gets the request body and deserializes it into the given type, using a [MarshallingService].
     *
     * The selected [MarshallingService] will determine what format data is returned.
     *
     * @param marshallingService The [MarshallingService] to use to deserialize this request body.
     * @param clazz The class to deserialize the data into.
     *
     * @return An instance of the class populated by the provided input data.
     */
    fun <T> getRequestBodyAs(marshallingService: MarshallingService, clazz: Class<T>) : T

    /**
     * Gets the request body and deserializes it into a list of the given type, using a [MarshallingService].
     *
     * The selected [MarshallingService] will determine what format data is returned.
     *
     * @param marshallingService The [MarshallingService] to use to deserialize this request body.
     * @param clazz The class to deserialize the data into.
     *
     * @return A list of instances of the class populated by the provided input data.
     */
    fun <T> getRequestBodyAsList(marshallingService: MarshallingService, clazz: Class<T>) : List<T>
}

/**
 * Gets the request body and deserializes it into the given type, using a [MarshallingService].
 *
 * The selected [MarshallingService] will determine what format data is returned.
 *
 * @param marshallingService The [MarshallingService] to use to deserialize this request body.
 *
 * @return An instance of the class populated by the provided input data.
 */
inline fun <reified T> RPCRequestData.getRequestBodyAs(marshallingService: MarshallingService) : T {
    return getRequestBodyAs(marshallingService, T::class.java)
}

/**
 * Gets the request body and deserializes it into a list of the given type, using a [MarshallingService].
 *
 * The selected [MarshallingService] will determine what format data is returned.
 *
 * @param marshallingService The [MarshallingService] to use to deserialize this request body.
 *
 * @return A list of instances of the class populated by the provided input data.
 */
inline fun <reified T> RPCRequestData.getRequestBodyAsList(marshallingService: MarshallingService) : List<T> {
    return getRequestBodyAsList(marshallingService, T::class.java)
}