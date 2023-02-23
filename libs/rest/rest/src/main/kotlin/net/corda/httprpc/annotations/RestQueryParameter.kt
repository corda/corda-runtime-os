package net.corda.rest.annotations

/**
 * Marks a function parameter of a function annotated with HTTP Endpoint annotation as a query parameter.
 *
 * @property name The name of the query parameter in the call. Defaults to the parameter's name in the function signature.
 * @property description The description of the path parameter, used for documentation. Defaults to empty string.
 * @property required Whether this parameter is required when making an HTTP call. Defaults to true.
 * @property default The default value of this parameter. Defaults to null.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class RestQueryParameter(
    val name: String = "",
    val description: String = "",
    val required: Boolean = true,
    val default: String = ""
)