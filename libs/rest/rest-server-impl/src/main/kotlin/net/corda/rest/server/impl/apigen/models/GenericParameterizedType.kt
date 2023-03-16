package net.corda.rest.server.impl.apigen.models

data class GenericParameterizedType(
    val clazz: Class<*>,
    val nestedParameterizedTypes: List<GenericParameterizedType> = emptyList()
)