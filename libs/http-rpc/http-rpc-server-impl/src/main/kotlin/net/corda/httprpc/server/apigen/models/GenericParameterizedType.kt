package net.corda.httprpc.server.apigen.models

data class GenericParameterizedType(
    val clazz: Class<*>,
    val nestedParameterizedTypes: List<GenericParameterizedType> = emptyList()
)