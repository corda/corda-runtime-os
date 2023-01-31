package net.corda.httprpc.annotations

import net.corda.httprpc.ResponseCode
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.httprpc.response.ResponseEntity

/**
 * Annotation that is meant to be applied on annotations to flag the fact that they are meant for exposing
 * an HTTP Endpoint.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class RestEndpoint

/**
 * Marks a function of an @[HttpRestResource] annotated interface to be exposed as a POST endpoint by the REST
 * generated web service.
 *
 * - If an endpoint successfully creates a resource, it should return a [ResponseEntity] with [ResponseCode.CREATED] (status code 201).
 * - If an endpoint successfully updates a resource or does some processing without creating a new resource, it should return a
 * [ResponseEntity] with [ResponseCode.OK] (status code 200).
 * - If an endpoint performs processing asynchronously, it should return a [ResponseEntity] with [ResponseCode.ACCEPTED] (status code 202).
 * The response payload for such an endpoint should contain a representation of the status of the request.
 * - If an endpoint method does some processing but has no result to return, the method can have no return type and by default
 * [ResponseCode.NO_CONTENT] (status code 204) is returned, unless an exception is thrown.
 *
 * @property path The relative path of the endpoint within its resource.
 *           Defaults to an empty string, meaning that path of the enclosing [HttpRestResource] should be used.
 * @property title The title of the endpoint, used for documentation. Defaults to the function name.
 * @property description The description of the endpoint, used for documentation. Defaults to empty string.
 * @property responseDescription The description of the response, used for documentation. Defaults to empty string.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@RestEndpoint
annotation class HttpPOST(
    val path: String = "",
    val title: String = "",
    val description: String = "",
    val responseDescription: String = ""
)

/**
 * Marks a function of an @[HttpRestResource] annotated interface to be exposed as a PUT endpoint by the REST
 * generated web service.
 *
 * - If an endpoint successfully creates a resource, it should return a [ResponseEntity] with [ResponseCode.CREATED] (status code 201).
 * - If an endpoint successfully updates a resource or does some processing without creating a new resource, it should return a
 * [ResponseEntity] with [ResponseCode.OK] (status code 200).
 * - If an endpoint performs processing asynchronously, it should return a [ResponseEntity] with [ResponseCode.ACCEPTED] (status code 202).
 * The response payload for such an endpoint should contain a representation of the status of the request.
 * - If an endpoint method does some processing but has no result to return, the method can have no return type and by default
 * [ResponseCode.NO_CONTENT] (status code 204) is returned, unless an exception is thrown.
 *
 * @property path The relative path of the endpoint within its resource.
 *           Defaults to an empty string, meaning that path of the enclosing [HttpRestResource] should be used.
 * @property title The title of the endpoint, used for documentation. Defaults to the function name.
 * @property description The description of the endpoint, used for documentation. Defaults to empty string.
 * @property responseDescription The description of the response, used for documentation. Defaults to empty string.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@RestEndpoint
annotation class HttpPUT(
    val path: String = "",
    val title: String = "",
    val description: String = "",
    val responseDescription: String = ""
)

/**
 * Marks a function or a property getter of an @[HttpRestResource] annotated interface to be exposed as a `GET`
 * endpoint by the REST generated web service.
 *
 * - Successful invocation of a GET API should return the representation of the requested resource.
 * - By default an endpoint does not need to return a [ResponseEntity], the return type will be converted to the response payload with
 * [ResponseCode.OK] (status code 200).
 * - If a resource cannot be found it should throw a [ResourceNotFoundException].
 *
 * @property path The relative path of the endpoint within its resource.
 *           Defaults to an empty string, meaning that path of the enclosing [HttpRestResource] should be used.
 * @property title The title of the endpoint, used for documentation. Defaults to the function name.
 * @property description The description of the endpoint, used for documentation. Defaults to empty string.
 * @property responseDescription The description of the response, used for documentation. Defaults to empty string.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
@RestEndpoint
annotation class HttpGET(
    val path: String = "",
    val title: String = "",
    val description: String = "",
    val responseDescription: String = ""
)

/**
 * Marks a function of an @[HttpRestResource] annotated interface to be exposed as a `DELETE`
 * endpoint by the REST generated web service.
 *
 * - If an endpoint successfully deletes a resource, it should return either a [ResponseEntity] with [ResponseCode.OK] (status code 200) and
 * response payload containing a representation of the status, or a [ResponseCode.NO_CONTENT] and no response payload.
 * - By default if the method has no return type [ResponseCode.NO_CONTENT] (status code 204) is returned, unless an exception is thrown.
 * - If an endpoint performs processing asynchronously, it should return a [ResponseEntity] with [ResponseCode.ACCEPTED] (status code 202).
 * The response payload for such an endpoint should contain a representation of the status of the request.
 *
 * @property path The relative path of the endpoint within its resource.
 *           Defaults to an empty string, meaning that path of the enclosing [HttpRestResource] should be used.
 * @property title The title of the endpoint, used for documentation. Defaults to the function name.
 * @property description The description of the endpoint, used for documentation. Defaults to empty string.
 * @property responseDescription The description of the response, used for documentation. Defaults to empty string.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@RestEndpoint
annotation class HttpDELETE(
    val path: String = "",
    val title: String = "",
    val description: String = "",
    val responseDescription: String = ""
)

/**
 * Marks a function of an @[HttpRestResource] annotated interface to be exposed as a Websocket
 * endpoint by the REST generated web service.
 *
 * @property path The relative path of the endpoint within its resource.
 *           Defaults to an empty string, meaning that path of the enclosing [HttpRestResource] should be used.
 * @property title The title of the endpoint, used for documentation. Defaults to the function name.
 * @property description The description of the endpoint, used for documentation. Defaults to empty string.
 * @property responseDescription The description of the response, used for documentation. Defaults to empty string.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@RestEndpoint
annotation class HttpWS(
    val path: String = "",
    val title: String = "",
    val description: String = "",
    val responseDescription: String = ""
)

fun Annotation.isRestEndpointAnnotation(): Boolean {
    return this.annotationClass.annotations.any { it is RestEndpoint }
}