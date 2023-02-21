@file:JvmName("RPCRequestUtils")
package net.corda.v5.application.flows

import net.corda.v5.application.marshalling.MarshallingService

/**
 * Gets the request body and deserializes it into the given type, using a [MarshallingService].
 *
 * The selected [MarshallingService] will determine what format data is returned.
 *
 * @param marshallingService The [MarshallingService] to use to deserialize this request body.
 *
 * @return An instance of the class populated by the provided input data.
 */
inline fun <reified T> ClientRequestBody.getRequestBodyAs(marshallingService: MarshallingService) : T {
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
inline fun <reified T> ClientRequestBody.getRequestBodyAsList(marshallingService: MarshallingService) : List<T> {
    return getRequestBodyAsList(marshallingService, T::class.java)
}
