package net.corda.rest.server.impl.apigen.processing.openapi.schema.builders

import net.corda.rest.server.impl.apigen.models.GenericParameterizedType
import net.corda.rest.server.impl.apigen.processing.openapi.schema.ParameterizedClass
import net.corda.rest.server.impl.apigen.processing.openapi.schema.SchemaModelProvider
import net.corda.rest.server.impl.apigen.processing.openapi.schema.model.SchemaMapModel
import net.corda.rest.server.impl.apigen.processing.openapi.schema.model.SchemaModel

internal class SchemaMapBuilder(private val schemaModelProvider: SchemaModelProvider) : SchemaBuilder {
    override val keys: List<Class<*>> = listOf(Map::class.java)

    override fun build(clazz: Class<*>, parameterizedClassList: List<GenericParameterizedType>): SchemaModel =
        SchemaMapModel(
            additionalProperties = parameterizedClassList.drop(1).singleOrNull()
                ?.let { schemaModelProvider.toSchemaModel(ParameterizedClass(it.clazz, it.nestedParameterizedTypes)) }
        )
}
