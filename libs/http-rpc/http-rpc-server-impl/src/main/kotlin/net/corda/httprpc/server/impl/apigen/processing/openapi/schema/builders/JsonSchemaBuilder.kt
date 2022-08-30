package net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders

import net.corda.httprpc.JsonObject
import net.corda.httprpc.server.impl.apigen.models.GenericParameterizedType
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.JsonSchemaModel
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaModel

internal class JsonSchemaBuilder : SchemaBuilder {
    override val keys = listOf(JsonObject::class.java)

    override fun build(clazz: Class<*>, parameterizedClassList: List<GenericParameterizedType>): SchemaModel{
        return JsonSchemaModel()
    }
}