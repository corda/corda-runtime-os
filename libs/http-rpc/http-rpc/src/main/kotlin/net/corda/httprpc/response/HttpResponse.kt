package net.corda.httprpc.response

import net.corda.httprpc.ResponseCode
import net.corda.httprpc.RpcOps

/**
 * This class can be used as a return type in [RpcOps] endpoints to allow control over the status code returned in the HTTP responses.
 *
 * For example, given the http specification (https://httpwg.org/specs/rfc9110.html), a POST that creates a new resource
 * should return a 201 Created. An asynchronous API that completes the processing of a request occurs at a later time should return a 202
 * Accepted.
 *
 * Use the static helper methods to create the appropriate http response.
 *
 * If an [RpcOps] endpoint function doesn't wrap its return type in a [HttpResponse], it will set the response's status code to
 * [ResponseCode.OK] (200) unless an exception is thrown.
 *
 * If an [RpcOps] endpoint has no return type at all, it will return a response with no body and a status code of [ResponseCode.NO_CONTENT]
 * (204) unless an exception is thrown.
 *
 * @param T the type of the response body payload, used in open-api generation.
 * @param responseCode the status code of the response.
 * @param responseBody the payload of the response. If null, the response payload will be "null".
 */
class HttpResponse<T : Any>(
    val responseCode: ResponseCode,
    val responseBody: T?,
) {
    companion object {
        fun <T : Any> ok(responseBody: T): HttpResponse<T> {
            return HttpResponse(ResponseCode.OK, responseBody)
        }
        fun <T : Any> resourceUpdated(responseBody: T): HttpResponse<T> {
            return HttpResponse(ResponseCode.OK, responseBody)
        }
        fun <T : Any> resourceCreated(responseBody: T): HttpResponse<T> {
            return HttpResponse(ResponseCode.CREATED, responseBody)
        }
        fun <T : Any> resourceDeleted(responseBody: T): HttpResponse<T> {
            return HttpResponse(ResponseCode.OK, responseBody)
        }
        fun <T : Any> requestAccepted(responseBody: T): HttpResponse<T> {
            return HttpResponse(ResponseCode.ACCEPTED, responseBody)
        }
        fun <T : Any> seeOther(responseBody: T): HttpResponse<T> {
            return HttpResponse(ResponseCode.SEE_OTHER, responseBody)
        }
    }
}
