package net.corda.httprpc

import net.corda.httprpc.exception.HttpApiException

/**
 * Use this enum when you want to customize the HTTP status code returned in error scenarios in HTTP APIs. Reuse error response codes where
 * appropriate when extending [HttpApiException].
 *
 * This enum will define all HTTP status codes and their causes. They also include a reason code which will help to identify particular
 * error responses and aid in debugging and support issues.
 *
 * This code will be on response messages and forms part of the public API. Adding message with new reason codes is not considered a
 * breaking change. Changing status or reason codes after release is considered a breaking change.
 *
 * Status codes:
 * 4XX - indicate a problem with the request. Requests can be re-submitted, usually with updated arguments and may succeed.
 * 5XX - indicate a problem occurred on the server side while processing the request. An application can't perform any action to correct a
 * 500-level error.
 *
 * @param statusCode the http status code for the http response.
 */
enum class ResponseCode constructor(val statusCode: Int) {
    /**
     * Signals the exception occurred due to invalid input data in the request or from a resource identified by the request.
     */
    BAD_REQUEST(400),

    /**
     * Signals the request was syntactically correct but contained data that was invalid to successfully complete the request.
     */
    INVALID_INPUT_DATA(400),

    /**
     * Signals the user authentication failed.
     */
    NOT_AUTHENTICATED(401),

    /**
     * Signals the user is not authorized to perform an action.
     */
    FORBIDDEN(403),

    /**
     * Signals the requested resource was not found.
     */
    RESOURCE_NOT_FOUND(404),

    /**
     * An error occurred internally.
     */
    INTERNAL_SERVER_ERROR(500),

    /**
     * An unexpected error occurred internally. Caused by programming logic failures such as NPE, most likely requires support intervention.
     */
    UNEXPECTED_ERROR(500),
    ;

    override fun toString(): String {
        return name
    }
}