package net.corda.httprpc.server.impl.apigen.models

data class GenericParameterizedType(
    val clazz: Class<*>,
    val nestedParameterizedTypes: List<GenericParameterizedType> = emptyList()
)