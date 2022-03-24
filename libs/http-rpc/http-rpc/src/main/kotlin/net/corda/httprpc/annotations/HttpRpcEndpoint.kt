package net.corda.httprpc.annotations

/**
 * Annotation that is meant to be applied on annotations to flag the fact that they are meant for exposing
 * an HTTP Endpoint.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class HttpRpcEndpoint

/**
 * Marks a function of an @[HttpRpcResource] annotated interface to be exposed as a POST endpoint by the HTTP RPC
 * generated web service.
 *
 * @property path The relative path of the endpoint within its resource.
 *           Defaults to an empty string, meaning that path of the enclosing [HttpRpcResource] should be used.
 * @property title The title of the endpoint, used for documentation. Defaults to the function name.
 * @property description The description of the endpoint, used for documentation. Defaults to empty string.
 * @property responseDescription The description of the response, used for documentation. Defaults to empty string.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@HttpRpcEndpoint
annotation class HttpRpcPOST(
    val path: String = "",
    val title: String = "",
    val description: String = "",
    val responseDescription: String = ""
)

/**
 * Marks a function of an @[HttpRpcResource] annotated interface to be exposed as a PUT endpoint by the HTTP RPC
 * generated web service.
 *
 * @property path The relative path of the endpoint within its resource.
 *           Defaults to an empty string, meaning that path of the enclosing [HttpRpcResource] should be used.
 * @property title The title of the endpoint, used for documentation. Defaults to the function name.
 * @property description The description of the endpoint, used for documentation. Defaults to empty string.
 * @property responseDescription The description of the response, used for documentation. Defaults to empty string.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@HttpRpcEndpoint
annotation class HttpRpcPUT(
    val path: String = "",
    val title: String = "",
    val description: String = "",
    val responseDescription: String = ""
)

/**
 * Marks a function or a property getter of an @[HttpRpcResource] annotated interface to be exposed as a `GET`
 * endpoint by the HTTP RPC generated web service.
 *
 * @property path The relative path of the endpoint within its resource.
 *           Defaults to an empty string, meaning that path of the enclosing [HttpRpcResource] should be used.
 * @property title The title of the endpoint, used for documentation. Defaults to the function name.
 * @property description The description of the endpoint, used for documentation. Defaults to empty string.
 * @property responseDescription The description of the response, used for documentation. Defaults to empty string.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
@HttpRpcEndpoint
annotation class HttpRpcGET(
    val path: String = "",
    val title: String = "",
    val description: String = "",
    val responseDescription: String = ""
)

fun Annotation.isRpcEndpointAnnotation(): Boolean {
    return this.annotationClass.annotations.any { it is HttpRpcEndpoint }
}