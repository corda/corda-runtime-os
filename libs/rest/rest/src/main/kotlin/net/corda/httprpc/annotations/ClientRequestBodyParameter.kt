package net.corda.httprpc.annotations

/**
 * Marks a function parameter of HTTP POST annotated function as a nested body parameter
 * of the top-level JSON object.
 *
 * @property name The name of the body parameter in the call. Defaults to the parameter's name in the function signature.
 * @property description The description of the parameter, used for documentation. Defaults to empty string.
 * @property required Whether the body is required when making HTTP call. Defaults to true.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class ClientRequestBodyParameter(
    val name: String = "",
    val description: String = "",
    val required: Boolean = true
)