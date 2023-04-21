package net.corda.rest.server.impl.apigen.processing.openapi.schema

import net.corda.rest.server.impl.apigen.models.GenericParameterizedType

internal data class ParameterizedClass(
    val clazz: Class<*>,
    val parameterizedClassList: List<GenericParameterizedType> = emptyList()
) {
    constructor(
        clazz: Class<*>,
        parameterizedClassList: List<GenericParameterizedType> = emptyList(),
        nullable: Boolean
    ): this(clazz, parameterizedClassList) {
        this.nullable = nullable
    }

    var nullable: Boolean? = null
    var required: Boolean = true // fields are required by default
    var name: String? = null
    var description: String? = null
}

val List<GenericParameterizedType>.mapKey
    get() = if (this.isEmpty()) ""
    else "_of_${this.joinToString("_") { it.clazz.simpleName }}"

