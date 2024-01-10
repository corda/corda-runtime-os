package net.corda.rest.server.impl.apigen.processing.openapi.schema.builders

import net.corda.rest.JsonObject
import net.corda.rest.server.impl.apigen.models.GenericParameterizedType
import net.corda.rest.server.impl.apigen.processing.openapi.schema.model.JsonSchemaModel
import net.corda.rest.server.impl.apigen.processing.openapi.schema.model.SchemaModel

internal class JsonSchemaBuilder : SchemaBuilder {
    override val keys = listOf(JsonObject::class.java)

    override fun build(clazz: Class<*>, parameterizedClassList: List<GenericParameterizedType>): SchemaModel{
        return JsonSchemaModel()
    }
}