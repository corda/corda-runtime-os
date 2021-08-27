package net.corda.v5.httprpc.api.annotations

/**
 * Marks a function of an @[HttpRpcResource] annotated interface to be exposed as a POST endpoint by the HTTP RPC generated web service.
 *
 * @property path The relative path of the endpoint within its resource. Defaults to the function name.
 * @property title The title of the endpoint, used for documentation. Defaults to the function name.
 * @property description The description of the endpoint, used for documentation. Defaults to empty string.
 * @property responseDescription The description of the response, used for documentation. Defaults to empty string.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class HttpRpcPOST (
        val path: String = "",
        val title: String = "",
        val description: String = "",
        val responseDescription: String = ""
)

/**
 * Marks a function function of an @[HttpRpcResource] annotated interface to be exposed as a GET endpoint by the HTTP RPC generated web service.
 *
 * @property path The relative path of the endpoint within its resource. Defaults to the function name.
 * @property title The title of the endpoint, used for documentation. Defaults to the function name.
 * @property description The description of the endpoint, used for documentation. Defaults to empty string.
 * @property responseDescription The description of the response, used for documentation. Defaults to empty string.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
annotation class HttpRpcGET (
        val path: String = "",
        val title: String = "",
        val description: String = "",
        val responseDescription: String = ""
)
