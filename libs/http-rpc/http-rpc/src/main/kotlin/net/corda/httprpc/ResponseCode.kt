package net.corda.httprpc

/**
 * Use this enum when you want to customize the HTTP status code returned in success responses and error scenarios in HTTP APIs.
 *
 * This enum will define all HTTP status codes and their causes. They also include a reason code which will help to identify particular
 * error responses and aid in debugging and support issues.
 *
 * This code will be on response messages and forms part of the public API. Adding message with new reason codes is not considered a
 * breaking change. Changing status or reason codes after release is considered a breaking change.
 *
 * Status codes:
 * 2XX - indicates a request was successfully received, understood and accepted.
 * 3XX - indicates further action needs to be taken in order to fulfill a request.
 * 4XX - indicates a problem with the request. Requests can be re-submitted, usually with updated arguments and may succeed.
 * 5XX - indicates a problem occurred on the server side that prevented it from fulfilling the request.
 *
 * @param statusCode the http status code for the http response.
 */
enum class ResponseCode constructor(val statusCode: Int) {

    /**
     * SUCCESSFUL 2xx
     */

    /**
     * Request has succeeded.
     *
     * See `https://httpwg.org/specs/rfc9110.html#status.200`.
     */
    OK(200),

    /**
     * One or more resources have been successfully created.
     *
     * See `https://httpwg.org/specs/rfc9110.html#status.201`.
     */
    CREATED(201),

    /**
     * The request has been accepted for processing but the processing has not been completed.
     *
     * See `https://httpwg.org/specs/rfc9110.html#status.202`.
     */
    ACCEPTED(202),

    /**
     * A request has succeeded but there is no content to send to the client.
     *
     * See `https://httpwg.org/specs/rfc9110.html#status.204`.
     */
    NO_CONTENT(204),

    /**
     * See `https://httpwg.org/specs/rfc9110.html#status.205`.
     */
    RESET_CONTENT(205),

    /**
     * See `https://httpwg.org/specs/rfc9110.html#status.206`.
     */
    PARTIAL_CONTENT(206),

    /**
     * REDIRECTION 3xx
     */

    /**
     * The requested resource is located at another URI using the GET HTTP method. Use this for response from asynchronous APIs that return
     * a status URI.
     *
     * See `https://httpwg.org/specs/rfc9110.html#status.303`.
     */
    SEE_OTHER(303),

    /**
     *
     *
     * See `https://httpwg.org/specs/rfc9110.html#status.306`.
     */
    UNUSED(306),

    /**
     * CLIENT ERRORS 4xx
     */

    /**
     * Signals the exception occurred due to invalid input data in the request or from a resource identified by the request.
     *
     * See `https://httpwg.org/specs/rfc9110.html#status.400`.
     */
    BAD_REQUEST(400),

    /**
     * Signals the request was syntactically correct but contained data that was invalid to successfully complete the request.
     *
     * See `https://httpwg.org/specs/rfc9110.html#status.400`.
     */
    INVALID_INPUT_DATA(400),

    /**
     * Signals the user authentication failed.
     *
     * See `https://httpwg.org/specs/rfc9110.html#status.401`.
     */
    NOT_AUTHENTICATED(401),

    /**
     * Signals the user is not authorized to perform an action.
     *
     * See `https://httpwg.org/specs/rfc9110.html#status.403`.
     */
    FORBIDDEN(403),

    /**
     * Signals the requested resource was not found.
     *
     * See `https://httpwg.org/specs/rfc9110.html#status.404`.
     */
    RESOURCE_NOT_FOUND(404),

    /**
     * See `https://httpwg.org/specs/rfc9110.html#status.405`.
     */
    METHOD_NOT_ALLOWED(405),

    /**
     * See `https://httpwg.org/specs/rfc9110.html#status.406`.
     */
    NOT_ACCEPTABLE(406),

    /**
     * See `https://httpwg.org/specs/rfc9110.html#status.408`.
     */
    REQUEST_TIMEOUT(408),

    /**
     * Signals the resource is not in the expected state.
     *
     * See `https://httpwg.org/specs/rfc9110.html#status.409`.
     */
    CONFLICT(409),

    /**
     * See `https://httpwg.org/specs/rfc9110.html#status.412`.
     */
    PRECONDITION_FAILED(412),

    /**
     * See `https://httpwg.org/specs/rfc9110.html#status.413`.
     */
    CONTENT_TOO_LARGE(413),

    /**
     * See `https://httpwg.org/specs/rfc9110.html#status.415`.
     */
    UNSUPPORTED_MEDIA_TYPE(415),

    /**
     * See `https://httpwg.org/specs/rfc9110.html#status.416`.
     */
    RANGE_NOT_SATISFIABLE(416),

    /**
     * See `https://httpwg.org/specs/rfc9110.html#status.422`.
     */
    UNPROCESSABLE_CONTENT(422),

    /**
     * SERVER ERRORS 5xx
     */

    /**
     * An unexpected condition occurred that prevented it from fulfilling the request.
     *
     * See `https://httpwg.org/specs/rfc9110.html#status.500`.
     */
    INTERNAL_SERVER_ERROR(500),

    /**
     * Common causes are a server that is down for maintenance or that is overloaded.
     * This response should be used for temporary conditions and the `Retry-After` HTTP header should, if possible,
     * contain the estimated time for the recovery of the service.
     *
     * See `https://httpwg.org/specs/rfc9110.html#status.503`.
     */
    SERVICE_UNAVAILABLE(503),

    /**
     * See `https://httpwg.org/specs/rfc9110.html#status.504`.
     */
    GATEWAY_TIMEOUT(504),

    /**
     * See `https://httpwg.org/specs/rfc9110.html#status.505`.
     */
    HTTP_VERSION_NOT_SUPPORTED(505),
    ;

    override fun toString(): String {
        return name
    }
}