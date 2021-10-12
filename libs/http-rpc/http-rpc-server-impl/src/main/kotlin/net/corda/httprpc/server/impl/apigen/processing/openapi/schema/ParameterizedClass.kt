package net.corda.httprpc.server.impl.apigen.processing.openapi.schema

import net.corda.httprpc.server.impl.apigen.models.GenericParameterizedType

internal data class ParameterizedClass(
    val clazz: Class<*>,
    val parameterizedClassList: List<GenericParameterizedType> = emptyList(),
    val nullable: Boolean = false
)

val List<GenericParameterizedType>.mapKey
    get() = if (this.isEmpty()) ""
    else "_of_${this.joinToString("_") { it.clazz.simpleName }}"

