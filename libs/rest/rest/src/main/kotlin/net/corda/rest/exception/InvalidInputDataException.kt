package net.corda.rest.exception

import net.corda.rest.ResponseCode

/**
 * The server validation of request data failed, the server could not complete the request because validation on the user's input failed.
 *
 * @param title the response title
 * @param details additional problem details
 * @param exceptionDetails contains cause and reason
 */
class InvalidInputDataException(
    title: String = "Invalid input data.",
    details: Map<String, String> = emptyMap(),
    exceptionDetails: ExceptionDetails? = null
) : HttpApiException(
    ResponseCode.BAD_REQUEST,
    title,
    details,
    exceptionDetails
)
