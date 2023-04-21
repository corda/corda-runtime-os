package net.corda.rest.server.impl.apigen.processing.openapi.schema.builders

import net.corda.rest.server.impl.apigen.models.GenericParameterizedType
import net.corda.rest.server.impl.apigen.processing.openapi.schema.model.SchemaEnumModel
import net.corda.rest.server.impl.apigen.processing.openapi.schema.model.SchemaModel

internal class SchemaEnumBuilder : SchemaBuilder {
    override val keys: List<Class<*>> = listOf(Enum::class.java)

    override fun build(clazz: Class<*>, parameterizedClassList: List<GenericParameterizedType>): SchemaModel =
        SchemaEnumModel(
            enum = clazz.enumConstants.map { it.toString() }
        )
}