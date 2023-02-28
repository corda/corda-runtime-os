package net.corda.rest.server.impl.apigen.models

internal data class Endpoint(
    /**
     * The API method (GET, POST).
     */
    val method: EndpointMethod,
    /**
     * The title of the endpoint, used in API spec.
     */
    val title: String,
    /**
     * The description of the endpoint, used in API spec.
     */
    val description: String,
    /**
     * The path of the endpoint, without containing the base API or the resource path.
     */
    val path: String?,
    /**
     * An ordered list of the invocation method parameters. These will be the endpoint parameters.
     */
    val parameters: List<EndpointParameter>,
    /**
     * The response body of the endpoint.
     */
    val responseBody: ResponseBody,
    /**
     * The method to invoke when this endpoint is triggered.
     */
    val invocationMethod: InvocationMethod
)

internal enum class EndpointMethod {
    POST,
    GET,
    PUT,
    DELETE,
    WS
}