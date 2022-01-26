package net.corda.httprpc.exception

import net.corda.httprpc.ResponseCode

/**
 * The server validation of request data failed, the server could not complete the request because validation on the user's input failed.
 *
 * @param message the response message
 * @param details additional problem details
 */
open class InvalidInputDataException(message: String = "Invalid input data.", details: Map<String, String> = emptyMap()) : HttpApiException(
    ResponseCode.INVALID_INPUT_DATA,
    message,
    details
)