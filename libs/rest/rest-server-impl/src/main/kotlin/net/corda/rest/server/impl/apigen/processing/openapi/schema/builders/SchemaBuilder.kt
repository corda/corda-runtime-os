package net.corda.rest.server.impl.apigen.processing.openapi.schema.builders

import net.corda.rest.server.impl.apigen.models.GenericParameterizedType
import net.corda.rest.server.impl.apigen.processing.openapi.schema.model.SchemaModel

/**
 * [SchemaBuilder] is responsible for building a [SchemaModel] out of the passed type.
 * This interface is intended to be implemented by any class that maps explicitly a type to a schema model.
 *
 */
interface SchemaBuilder {
    /**
     * list of supertypes, one of which has to be matched to the type in order to select this builder
     */
    val keys: List<Class<*>>

    fun isSupertypeOf(c: Class<*>): Boolean = keys.any { it.isAssignableFrom(c) }

    fun build(clazz: Class<*>, parameterizedClassList: List<GenericParameterizedType> = emptyList()): SchemaModel
}