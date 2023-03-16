package net.corda.rest.server.impl.apigen.models

data class EndpointParameter(
    /**
     * The name of the parameter in the function declaration.
     */
    val id: String,
    /**
     * The name of the parameter in the API (may be the same as the id).
     */
    val name: String,
    /**
     * The description of the parameter, used in the API spec.
     */
    val description: String,
    /**
     * Whether the parameter is required. Always true for path parameters.
     */
    val required: Boolean,
    /**
     * Whether the parameter is nullable. Defaults to false.
     */
    val nullable: Boolean = false,
    /**
     * The default value of the variable. Only relevant for query parameters.
     */
    val default: String?,
    /**
     * The class type of the variable, to enable casting.
     */
    val classType: Class<*>,
    /**
     * The type of the variable in the API (Path, Query, Body).
     */
    val type: ParameterType,
    /**
     * The parameterized types of the variable, as specified in its signature via generics
     */
    val parameterizedTypes: List<GenericParameterizedType> = emptyList(),
    /**
     * Whether this parameter represents a file to be uploaded.
     */
    val isFile: Boolean = false
)

enum class ParameterType {
    PATH, QUERY, BODY
}
