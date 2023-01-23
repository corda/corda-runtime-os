package net.corda.httprpc.response

import net.corda.httprpc.ResponseCode
import net.corda.httprpc.RestResource

/**
 * This class can be used as a return type in [RestResource] endpoints to allow control over the status code returned in
 * the HTTP responses.
 *
 * For example, given the http specification (https://httpwg.org/specs/rfc9110.html), a POST that creates a new resource
 * should return a 201 Created. An asynchronous API that completes the processing of a request occurs at a later time
 * should return a 202
 * Accepted.
 *
 * Use the static helper methods to create the appropriate http response.
 *
 * If an [RestResource] endpoint function doesn't wrap its return type in a [ResponseEntity], it will set the response's
 * status code to
 * [ResponseCode.OK] (200) unless an exception is thrown.
 *
 * If an [RestResource] endpoint has no return type at all, it will return a response with no body and a status code
 * of [ResponseCode.NO_CONTENT]
 * (204) unless an exception is thrown.
 *
 * @param T the type of the response body payload, used in open-api generation.
 * @param responseCode the status code of the response.
 * @param responseBody the payload of the response. If null, the response payload will be "null".
 */
class ResponseEntity<T : Any?>(
    val responseCode: ResponseCode,
    val responseBody: T,
) {
    companion object {
        fun <T : Any?> ok(responseBody: T): ResponseEntity<T> {
            return ResponseEntity(ResponseCode.OK, responseBody)
        }
        fun <T : Any?> updated(responseBody: T): ResponseEntity<T> {
            return ResponseEntity(ResponseCode.OK, responseBody)
        }
        fun <T : Any?> created(responseBody: T): ResponseEntity<T> {
            return ResponseEntity(ResponseCode.CREATED, responseBody)
        }
        fun <T : Any?> deleted(responseBody: T): ResponseEntity<T> {
            return ResponseEntity(ResponseCode.OK, responseBody)
        }
        fun <T : Any?> accepted(responseBody: T): ResponseEntity<T> {
            return ResponseEntity(ResponseCode.ACCEPTED, responseBody)
        }
        fun <T : Any?> seeOther(responseBody: T): ResponseEntity<T> {
            return ResponseEntity(ResponseCode.SEE_OTHER, responseBody)
        }
    }
}
