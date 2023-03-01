package net.corda.rest.annotations

/**
 * Marks a function parameter of a function annotated with HTTP Endpoint annotation as a path parameter.

 * Path parameters need to also be defined in the endpoint's path, in the form of `/{parameter}/`.
 *
 * @property name The name of the path parameter within the endpoint's path.
 *      Defaults to the parameter's name in the function signature.
 * @property description The description of the path parameter, used for documentation. Defaults to empty string.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class RestPathParameter(
    val name: String = "",
    val description: String = ""
)